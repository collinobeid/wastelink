package com.waste.link.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.waste.link.data.auth.SessionStateProvider
import com.waste.link.data.local.WasteLinkDatabase
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.remote.RemoteRequestOutcome
import com.waste.link.data.remote.WasteRequestRemoteDataSource
import com.waste.link.data.repository.LocalWasteRequestRepository
import com.waste.link.data.repository.LocalWasteRequestRepositoryImpl
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WasteRequestMultiUserAndWorkerTest {

    private lateinit var context: Context
    private lateinit var database: WasteLinkDatabase
    private lateinit var localRepository: LocalWasteRequestRepository
    private lateinit var fakeRemoteDataSource: FakeRemoteDataSource
    private lateinit var fakeSessionProvider: FakeSessionProvider
    private lateinit var fakeSyncScheduler: FakeWasteRequestSyncScheduler
    private lateinit var orchestrator: WasteRequestSyncOrchestrator

    private val userA = User(
        id = "user_a",
        fullName = "Resident A",
        email = "a@wastelink.tz",
        phoneNumber = "+255712345671",
        role = UserRole.RESIDENT,
        accountStatus = AccountStatus.ACTIVE
    )

    private val userB = User(
        id = "user_b",
        fullName = "Resident B",
        email = "b@wastelink.tz",
        phoneNumber = "+255712345672",
        role = UserRole.RESIDENT,
        accountStatus = AccountStatus.ACTIVE
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WasteLinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        localRepository = LocalWasteRequestRepositoryImpl(
            database = database,
            draftDao = database.residentDraftDao(),
            requestDao = database.localWasteRequestDao()
        )
        fakeRemoteDataSource = FakeRemoteDataSource()
        fakeSessionProvider = FakeSessionProvider(
            SessionState.Authenticated(user = userA, isEmailVerified = true)
        )
        fakeSyncScheduler = FakeWasteRequestSyncScheduler()
        orchestrator = WasteRequestSyncOrchestrator(
            localRepository = localRepository,
            remoteDataSource = fakeRemoteDataSource,
            sessionStateProvider = fakeSessionProvider,
            syncScheduler = fakeSyncScheduler
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleRequest(
        id: String,
        clientUid: String
    ): WasteRequest {
        val coords = GeoCoordinates(-6.7924, 39.2083)
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            clientUid = clientUid,
            coordinates = coords,
            serviceZoneId = "zone_kinondoni",
            pickupAddress = "Masaki, Dar es Salaam",
            categoryId = "cat_general",
            volumeTier = VolumeTier.STANDARD_BIN
        )
    }

    // =========================================================================
    // AC-09: Multi-user session isolation & account switching
    // =========================================================================
    @Test
    fun ac09_accountSwitching_doesNotLeakOrMutateOtherUserQueuedRequests() = runBlocking {
        // Step 1: User A persists two requests as QUEUED_OFFLINE
        val reqA1 = sampleRequest("req_a1", "user_a")
        val reqA2 = sampleRequest("req_a2", "user_a")
        localRepository.saveLocalRequest("user_a", reqA1, LocalSyncStatus.QUEUED_OFFLINE)
        localRepository.saveLocalRequest("user_a", reqA2, LocalSyncStatus.QUEUED_OFFLINE)

        // Step 2: User A logs out; User B logs in
        fakeSessionProvider.mutableSessionState.value = SessionState.Authenticated(user = userB, isEmailVerified = true)

        // Step 3: User B creates a request
        val reqB1 = sampleRequest("req_b1", "user_b")
        localRepository.saveLocalRequest("user_b", reqB1, LocalSyncStatus.QUEUED_OFFLINE)

        // Step 4: Run sync for User B
        val resultsB = orchestrator.synchronizeQueuedRequests("user_b")

        // Only User B's request was synced
        assertEquals(1, resultsB.size)
        assertTrue(resultsB[0] is SyncResult.Success)
        assertEquals(1, fakeRemoteDataSource.submissions.size)
        assertEquals("user_b", fakeRemoteDataSource.submissions[0].clientUid)

        // Step 5: Verify User A's requests were NEVER touched or uploaded
        val recordA1 = localRepository.getLocalRecord("req_a1", "user_a")
        val recordA2 = localRepository.getLocalRecord("req_a2", "user_a")
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, recordA1!!.syncStatus)
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, recordA2!!.syncStatus)

        // Step 6: User B logs out; User A logs back in
        fakeSessionProvider.mutableSessionState.value = SessionState.Authenticated(user = userA, isEmailVerified = true)
        val resultsA = orchestrator.synchronizeQueuedRequests("user_a")

        assertEquals(2, resultsA.size)
        assertTrue(resultsA.all { it is SyncResult.Success })
        assertEquals(3, fakeRemoteDataSource.submissions.size) // 1 from B + 2 from A
    }

    // =========================================================================
    // AC-08: Concurrent sync attempts do not duplicate remote writes
    // =========================================================================
    @Test
    fun ac08_concurrentSyncAttempts_executesSingleRemoteWrite() = runBlocking {
        val request = sampleRequest("concurrent_req_1", "user_a")
        localRepository.saveLocalRequest("user_a", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Launch 5 concurrent sync calls on the same request
        val deferreds = (1..5).map {
            async {
                orchestrator.synchronizeSingleRequest("user_a", request.id)
            }
        }
        val outcomes = deferreds.awaitAll()

        // At least one returned Success, and others either Success or QueuedOffline
        assertTrue(outcomes.any { it is SyncResult.Success })

        // Exactly ONE remote createRequest was executed!
        assertEquals(1, fakeRemoteDataSource.submissions.size)

        // Local record is SYNCED
        val record = localRepository.getLocalRecord(request.id, "user_a")
        assertNotNull(record)
        assertEquals(LocalSyncStatus.SYNCED, record!!.syncStatus)
    }

    // =========================================================================
    // WasteRequestSyncWorker unique name formatting
    // =========================================================================
    @Test
    fun workerUniqueName_formatsWithUid() {
        val name = WasteRequestSyncWorker.uniqueWorkName("user_1234")
        assertEquals("sync_waste_requests_user_1234", name)
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================
    class FakeSessionProvider(
        initialState: SessionState
    ) : SessionStateProvider {
        val mutableSessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()
    }

    class FakeWasteRequestSyncScheduler : WasteRequestSyncScheduler {
        val scheduledImmediate = mutableListOf<String>()
        val scheduledDelayed = mutableListOf<Triple<String, Long, Int>>()
        val cancelled = mutableListOf<String>()

        override fun scheduleImmediateSync(authenticatedUid: String) {
            scheduledImmediate.add(authenticatedUid)
        }

        override fun scheduleDelayedSync(authenticatedUid: String, delayMs: Long, retryAttempt: Int) {
            scheduledDelayed.add(Triple(authenticatedUid, delayMs, retryAttempt))
        }

        override fun cancelSync(authenticatedUid: String) {
            cancelled.add(authenticatedUid)
        }
    }

    class FakeRemoteDataSource : WasteRequestRemoteDataSource {
        val submissions = mutableListOf<WasteRequest>()

        override suspend fun createRequest(
            authenticatedUid: String,
            request: WasteRequest
        ): RemoteRequestOutcome {
            submissions.add(request)
            return RemoteRequestOutcome.Success(
                confirmedRequest = request.copy(requestRef = "WL-2611-TEST01"),
                requestRef = "WL-2611-TEST01",
                serverCreatedAtEpochMs = System.currentTimeMillis(),
                serverUpdatedAtEpochMs = System.currentTimeMillis()
            )
        }

        override suspend fun getRequestById(
            authenticatedUid: String,
            requestId: String,
            serviceZoneId: String?
        ): RemoteRequestOutcome {
            return RemoteRequestOutcome.Rejected("NOT_FOUND", "Not found")
        }
    }
}
