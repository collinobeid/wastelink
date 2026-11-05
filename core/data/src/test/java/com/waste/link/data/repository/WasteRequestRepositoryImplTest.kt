package com.waste.link.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.waste.link.data.auth.SessionStateProvider
import com.waste.link.data.local.WasteLinkDatabase
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.remote.RemoteRequestOutcome
import com.waste.link.data.remote.WasteRequestRemoteDataSource
import com.waste.link.data.sync.WasteRequestSyncOrchestrator
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import com.waste.link.domain.repository.CreateWasteRequestOutcome
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WasteRequestRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var database: WasteLinkDatabase
    private lateinit var localRepository: LocalWasteRequestRepository
    private lateinit var fakeRemoteDataSource: FakeRemoteDataSource
    private lateinit var fakeSessionProvider: FakeSessionProvider
    private lateinit var orchestrator: WasteRequestSyncOrchestrator
    private lateinit var repository: WasteRequestRepositoryImpl

    private val authUser = User(
        id = "resident_1",
        fullName = "Test Resident",
        email = "resident@wastelink.tz",
        phoneNumber = "+255712345678",
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
            SessionState.Authenticated(user = authUser, isEmailVerified = true)
        )
        orchestrator = WasteRequestSyncOrchestrator(
            localRepository = localRepository,
            remoteDataSource = fakeRemoteDataSource,
            sessionStateProvider = fakeSessionProvider
        )
        repository = WasteRequestRepositoryImpl(
            localRepository = localRepository,
            syncOrchestrator = orchestrator,
            sessionStateProvider = fakeSessionProvider
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleRequest(
        id: String = "test-req-1",
        clientUid: String = "resident_1"
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

    @Test
    fun createWasteRequest_unauthenticated_returnsFailureWithoutPersistence() = runBlocking {
        fakeSessionProvider.mutableSessionState.value = SessionState.Unauthenticated

        val request = sampleRequest()
        val outcome = repository.createWasteRequest(request)

        assertTrue(outcome is CreateWasteRequestOutcome.Failure)
        val failure = outcome as CreateWasteRequestOutcome.Failure
        assertTrue(failure.error is IllegalStateException)
        assertEquals(0, fakeRemoteDataSource.submissions.size)

        val record = localRepository.getLocalRecord(request.id, "resident_1")
        assertTrue(record == null)
    }

    @Test
    fun createWasteRequest_uidMismatch_returnsFailureWithoutPersistence() = runBlocking {
        // Request belongs to attacker, but authenticated user is resident_1
        val request = sampleRequest(clientUid = "victim_uid")
        val outcome = repository.createWasteRequest(request)

        assertTrue(outcome is CreateWasteRequestOutcome.Failure)
        val failure = outcome as CreateWasteRequestOutcome.Failure
        assertTrue(failure.error is IllegalArgumentException)
        assertEquals(0, fakeRemoteDataSource.submissions.size)
    }

    @Test
    fun createWasteRequest_happyPath_persistsLocallyAndReturnsConfirmedRequest() = runBlocking {
        val request = sampleRequest()
        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.Success(
            confirmedRequest = request.copy(requestRef = "WL-2611-7X9F2B"),
            requestRef = "WL-2611-7X9F2B",
            serverCreatedAtEpochMs = 1793869200000L,
            serverUpdatedAtEpochMs = 1793869200000L
        )

        val outcome = repository.createWasteRequest(request)

        assertTrue(outcome is CreateWasteRequestOutcome.Success)
        val success = outcome as CreateWasteRequestOutcome.Success
        assertEquals("WL-2611-7X9F2B", success.confirmedRequest.requestRef)

        // Verify locally persisted in Room as SYNCED
        val record = localRepository.getLocalRecord(request.id, "resident_1")
        assertNotNull(record)
        assertEquals(LocalSyncStatus.SYNCED, record!!.syncStatus)
        assertEquals("WL-2611-7X9F2B", record.requestRef)
    }

    @Test
    fun createWasteRequest_networkFailure_retainsRecordLocallyWithSyncFailed() = runBlocking {
        val request = sampleRequest()
        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.TransientFailure(
            cause = IOException("No internet connection")
        )

        val outcome = repository.createWasteRequest(request)

        assertTrue(outcome is CreateWasteRequestOutcome.Failure)

        // Verify request was safely persisted in Room despite remote failure
        val record = localRepository.getLocalRecord(request.id, "resident_1")
        assertNotNull(record)
        assertEquals(LocalSyncStatus.SYNC_FAILED, record!!.syncStatus)
        assertEquals(1, record.retryCount)
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

    class FakeRemoteDataSource : WasteRequestRemoteDataSource {
        val submissions = mutableListOf<WasteRequest>()
        var nextOutcome: RemoteRequestOutcome? = null

        override suspend fun createRequest(
            authenticatedUid: String,
            request: WasteRequest
        ): RemoteRequestOutcome {
            submissions.add(request)
            return nextOutcome ?: RemoteRequestOutcome.Success(
                confirmedRequest = request.copy(requestRef = "WL-2611-TEST01"),
                requestRef = "WL-2611-TEST01",
                serverCreatedAtEpochMs = System.currentTimeMillis(),
                serverUpdatedAtEpochMs = System.currentTimeMillis()
            )
        }

        override suspend fun getRequestById(
            authenticatedUid: String,
            requestId: String
        ): RemoteRequestOutcome {
            return nextOutcome ?: RemoteRequestOutcome.Rejected("NOT_FOUND", "Not found")
        }
    }
}
