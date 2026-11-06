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
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WasteRequestSyncOrchestratorTest {

    private lateinit var context: Context
    private lateinit var database: WasteLinkDatabase
    private lateinit var localRepository: LocalWasteRequestRepository
    private lateinit var fakeRemoteDataSource: FakeWasteRequestRemoteDataSource
    private lateinit var fakeSessionProvider: FakeSessionStateProvider
    private lateinit var fakeSyncScheduler: FakeWasteRequestSyncScheduler
    private lateinit var orchestrator: WasteRequestSyncOrchestrator

    private val authResidentUser = User(
        id = "resident_uid_1",
        fullName = "Juma Resident",
        email = "juma@wastelink.tz",
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
        fakeRemoteDataSource = FakeWasteRequestRemoteDataSource()
        fakeSessionProvider = FakeSessionStateProvider(
            SessionState.Authenticated(user = authResidentUser, isEmailVerified = true)
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
        id: String = "test-uuid-1",
        clientUid: String = "resident_uid_1",
        serviceZoneId: String = "zone_kinondoni"
    ): WasteRequest {
        val coords = GeoCoordinates(-6.7924, 39.2083)
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            clientUid = clientUid,
            coordinates = coords,
            serviceZoneId = serviceZoneId,
            pickupAddress = "Masaki, Dar es Salaam",
            categoryId = "cat_general",
            volumeTier = VolumeTier.STANDARD_BIN
        )
    }

    // =========================================================================
    // AC-01: Authenticated resident syncs eligible request -> Room transitions to SYNCED
    // =========================================================================
    @Test
    fun ac01_authenticatedResidentSyncsEligibleRequest_transitionsToSynced() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest(
            authenticatedUid = "resident_uid_1",
            request = request,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.Success(
            confirmedRequest = request.copy(requestRef = "WL-2611-7X9F2B"),
            requestRef = "WL-2611-7X9F2B",
            serverCreatedAtEpochMs = 1793869200000L,
            serverUpdatedAtEpochMs = 1793869200000L
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.Success)
        assertEquals("WL-2611-7X9F2B", (result as SyncResult.Success).requestRef)

        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertNotNull(record)
        assertEquals(LocalSyncStatus.SYNCED, record!!.syncStatus)
        assertEquals("WL-2611-7X9F2B", record.requestRef)
        assertEquals(1793869200000L, record.createdAtEpochMs)
        assertEquals(1793869200000L, record.updatedAtEpochMs)
        assertNull(record.syncErrorMessage)
    }

    // =========================================================================
    // AC-02: Unauthenticated user cannot sync requests -> halts with AuthUnavailable
    // =========================================================================
    @Test
    fun ac02_unauthenticatedUser_haltsWithAuthUnavailable() = runBlocking {
        fakeSessionProvider.mutableSessionState.value = SessionState.Unauthenticated

        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.AuthUnavailable)
        assertEquals(0, fakeRemoteDataSource.submissions.size)

        // Local record remains QUEUED_OFFLINE
        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, record!!.syncStatus)
    }

    // =========================================================================
    // AC-03: User A's requests cannot be uploaded under User B -> rejected UID_MISMATCH
    // =========================================================================
    @Test
    fun ac03_uidMismatch_rejectedWithoutRemoteWrite() = runBlocking {
        val request = sampleRequest(clientUid = "resident_uid_1")
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Caller attempts to sync as attacker_uid_2 while logged in as resident_uid_1
        val result = orchestrator.synchronizeSingleRequest("attacker_uid_2", request.id)

        assertTrue(result is SyncResult.Rejected)
        val rejected = result as SyncResult.Rejected
        assertEquals("UID_MISMATCH", rejected.errorCode)
        assertEquals(0, fakeRemoteDataSource.submissions.size)
    }

    // =========================================================================
    // AC-04: Idempotent reconciliation -> AlreadyExists transitions to SYNCED
    // =========================================================================
    @Test
    fun ac04_alreadyExistsOnRemote_reconcilesToSyncedWithoutDuplicates() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.AlreadyExists(
            existingRequest = request.copy(requestRef = "WL-2611-7X9F2B"),
            requestRef = "WL-2611-7X9F2B",
            serverCreatedAtEpochMs = 1793869200000L,
            serverUpdatedAtEpochMs = 1793869200000L
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.Success)
        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.SYNCED, record!!.syncStatus)
        assertEquals("WL-2611-7X9F2B", record.requestRef)
        assertEquals(1793869200000L, record.createdAtEpochMs)
        assertEquals(1793869200000L, record.updatedAtEpochMs)
    }

    // =========================================================================
    // AC-05: Server rejection (PERMISSION_DENIED) -> Room transitions to SYNC_FAILED
    // =========================================================================
    @Test
    fun ac05_serverRejection_transitionsToSyncFailed() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.Rejected(
            errorCode = "PERMISSION_DENIED",
            message = "Caller account is locked."
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.Rejected)
        val rejected = result as SyncResult.Rejected
        assertEquals("PERMISSION_DENIED", rejected.errorCode)

        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.SYNC_FAILED, record!!.syncStatus)
        assertTrue(record.syncErrorMessage!!.contains("PERMISSION_DENIED"))
    }

    // =========================================================================
    // AC-06: Network failure -> QUEUED_OFFLINE with retry scheduled (Finding 1)
    // =========================================================================
    @Test
    fun ac06_transientNetworkFailure_schedulesRetryAndBackoff() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.TransientFailure(
            cause = IOException("Connection reset")
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.QueuedOffline)

        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        // Finding 1: Retryable failure remains in QUEUED_OFFLINE for subsequent worker attempt
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, record!!.syncStatus)
        assertEquals(1, record.retryCount)
        assertEquals("Connection reset", record.syncErrorMessage)
    }

    // =========================================================================
    // AC-07: Interrupted upload recovery resets stale UPLOADING to QUEUED_OFFLINE
    // =========================================================================
    @Test
    fun ac07_interruptedUploadRecovery_resetsStaleRecords() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Set state to UPLOADING with stale timestamp (2 minutes ago)
        val currentMs = 1793869200000L
        orchestrator.clock = { currentMs }
        localRepository.updateSyncStatus(
            id = request.id,
            clientUid = "resident_uid_1",
            newStatus = LocalSyncStatus.UPLOADING,
            attemptEpochMs = currentMs - 120_000L // 2 min ago
        )

        val recoveredCount = orchestrator.recoverInterruptedUploads("resident_uid_1", staleThresholdMs = 60_000L)

        assertEquals(1, recoveredCount)
        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, record!!.syncStatus)
    }

    // =========================================================================
    // AC-10: Legacy unmapped records remain excluded and quarantined
    // =========================================================================
    @Test
    fun ac10_legacyUnmappedZone_quarantinedAndNeverSentToRemote() = runBlocking {
        val request = sampleRequest(serviceZoneId = "legacy_unmapped_zone")
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.Rejected)
        val rejected = result as SyncResult.Rejected
        assertEquals("LEGACY_UNMAPPED_ZONE", rejected.errorCode)
        assertEquals(0, fakeRemoteDataSource.submissions.size)

        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.SYNC_FAILED, record!!.syncStatus)
        assertTrue(record.syncErrorMessage!!.contains("quarantined"))
    }

    // =========================================================================
    // AC-13: Max retries exceeded -> fails permanently
    // =========================================================================
    @Test
    fun ac13_maxRetriesExceeded_transitionsToTerminalFailure() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Set retry count to 3 (already attempted 3 times)
        localRepository.updateSyncStatus(
            id = request.id,
            clientUid = "resident_uid_1",
            newStatus = LocalSyncStatus.SYNC_FAILED,
            retryCount = 3
        )

        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.TransientFailure(
            cause = IOException("Gateway Timeout")
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.Failure)
        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.SYNC_FAILED, record!!.syncStatus)
        assertEquals(4, record.retryCount)
        assertTrue(record.syncErrorMessage!!.contains("Max retries exceeded"))
        // Finding 5: Max retries exceeded must NOT schedule another retry!
        assertTrue(fakeSyncScheduler.scheduledDelayed.isEmpty())
    }

    @Test
    fun finding4_sessionChangeDuringUpload_abortsCommit_resetsRecordToQueuedOffline() = runBlocking {
        val userB = User(
            id = "resident_uid_2",
            fullName = "Amina Resident",
            email = "amina@wastelink.tz",
            phoneNumber = "+255712345679",
            role = UserRole.RESIDENT,
            accountStatus = AccountStatus.ACTIVE
        )

        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Simulate session switching to User B while the remote upload is suspended in-flight
        fakeRemoteDataSource.onBeforeCreate = {
            fakeSessionProvider.mutableSessionState.value = SessionState.Authenticated(
                user = userB,
                isEmailVerified = true
            )
        }

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        // Result must be AuthUnavailable
        assertTrue(result is SyncResult.AuthUnavailable)

        // Original user's record must NOT be marked SYNCED; must be safely reset to QUEUED_OFFLINE
        val userARecord = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertNotNull(userARecord)
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, userARecord!!.syncStatus)
        assertTrue(userARecord.syncErrorMessage!!.contains("session change"))

        // User B must have no records created
        val userBRecords = localRepository.getQueuedRecords("resident_uid_2")
        assertTrue(userBRecords.isEmpty())
    }

    @Test
    fun finding5_transientFailure_schedulesWorkManagerDelayedRetry() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.TransientFailure(
            cause = IOException("Transient Socket Timeout")
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        assertTrue(result is SyncResult.QueuedOffline)
        val (scheduledUid, delayMs, retryAttempt) = fakeSyncScheduler.scheduledDelayed.first()
        assertEquals("resident_uid_1", scheduledUid)
        assertEquals(orchestrator.computeBackoffDelay(1), delayMs)
        assertEquals(1, retryAttempt)
    }

    @Test
    fun finding3_unresolvedServerTimestamps_treatedAsTransientFailure() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Server returns success but with unresolved 0L timestamps
        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.Success(
            confirmedRequest = request.copy(requestRef = "WL-2611-TEST01"),
            requestRef = "WL-2611-TEST01",
            serverCreatedAtEpochMs = 0L,
            serverUpdatedAtEpochMs = 0L
        )

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        // Must NOT report success
        assertTrue(result is SyncResult.QueuedOffline)
        val record = localRepository.getLocalRecord(request.id, "resident_uid_1")
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, record!!.syncStatus)
        assertEquals(1, record.retryCount)
        assertTrue(record.syncErrorMessage!!.contains("unresolved"))
    }

    @Test
    fun finding1_backoffWindowRespected_skipsPrematureRetry() = runBlocking {
        val request = sampleRequest()
        val baseTime = 1793869200000L
        orchestrator.clock = { baseTime }

        localRepository.saveLocalRequest("resident_uid_1", request, LocalSyncStatus.QUEUED_OFFLINE)
        localRepository.updateSyncStatus(
            id = request.id,
            clientUid = "resident_uid_1",
            newStatus = LocalSyncStatus.QUEUED_OFFLINE,
            attemptEpochMs = baseTime,
            retryCount = 1
        )

        // Advance clock by only 500ms (backoff for retry 1 is 3000ms)
        orchestrator.clock = { baseTime + 500L }

        val result = orchestrator.synchronizeSingleRequest("resident_uid_1", request.id)

        // Should skip remote write because backoff has not elapsed
        assertTrue(result is SyncResult.QueuedOffline)
        assertTrue((result as SyncResult.QueuedOffline).reason.contains("Retry backoff in progress"))
        assertEquals(0, fakeRemoteDataSource.submissions.size)
    }

    @Test
    fun finding1_synchronizeQueuedRequests_preservesRemainingBackoffs() = runBlocking {
        val baseTime = 1793869200000L
        orchestrator.clock = { baseTime }

        // Request 1: in backoff (failed at baseTime, retry 1)
        val req1 = sampleRequest(id = "req-1")
        localRepository.saveLocalRequest("resident_uid_1", req1, LocalSyncStatus.QUEUED_OFFLINE)
        localRepository.updateSyncStatus(
            id = "req-1",
            clientUid = "resident_uid_1",
            newStatus = LocalSyncStatus.QUEUED_OFFLINE,
            attemptEpochMs = baseTime,
            retryCount = 1
        )

        // Request 2: new request, ready immediately (retryCount = 0)
        val req2 = sampleRequest(id = "req-2")
        localRepository.saveLocalRequest("resident_uid_1", req2, LocalSyncStatus.QUEUED_OFFLINE)

        // Advance clock by 1000ms (less than 3000ms backoff for req1)
        orchestrator.clock = { baseTime + 1000L }

        val results = orchestrator.synchronizeQueuedRequests("resident_uid_1")

        assertEquals(2, results.size)
        // req1 skipped due to backoff
        assertTrue(results[0] is SyncResult.QueuedOffline)
        // req2 synced successfully
        assertTrue(results[1] is SyncResult.Success)

        // Delayed work must be re-scheduled for req1's remaining backoff (2000ms)
        assertTrue(fakeSyncScheduler.scheduledDelayed.isNotEmpty())
        val delayed = fakeSyncScheduler.scheduledDelayed.last()
        assertEquals("resident_uid_1", delayed.first)
        assertEquals(2000L, delayed.second)
        assertEquals(1, delayed.third)
    }

    // =========================================================================
    // Helper Test Doubles
    // =========================================================================
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

    class FakeSessionStateProvider(
        initialState: SessionState
    ) : SessionStateProvider {
        val mutableSessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()
    }

    class FakeWasteRequestRemoteDataSource : WasteRequestRemoteDataSource {
        val submissions = mutableListOf<WasteRequest>()
        var nextOutcome: RemoteRequestOutcome? = null
        var onBeforeCreate: (() -> Unit)? = null

        override suspend fun createRequest(
            authenticatedUid: String,
            request: WasteRequest
        ): RemoteRequestOutcome {
            onBeforeCreate?.invoke()
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
            requestId: String,
            serviceZoneId: String?
        ): RemoteRequestOutcome {
            return nextOutcome ?: RemoteRequestOutcome.Rejected("NOT_FOUND", "Not found")
        }
    }
}
