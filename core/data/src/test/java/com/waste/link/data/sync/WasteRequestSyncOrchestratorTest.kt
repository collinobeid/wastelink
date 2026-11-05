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
        orchestrator = WasteRequestSyncOrchestrator(
            localRepository = localRepository,
            remoteDataSource = fakeRemoteDataSource,
            sessionStateProvider = fakeSessionProvider
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
    // AC-06: Network failure -> SYNC_FAILED with retry scheduled
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
        assertEquals(LocalSyncStatus.SYNC_FAILED, record!!.syncStatus)
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
    }

    // =========================================================================
    // Helper Test Doubles
    // =========================================================================
    class FakeSessionStateProvider(
        initialState: SessionState
    ) : SessionStateProvider {
        val mutableSessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()
    }

    class FakeWasteRequestRemoteDataSource : WasteRequestRemoteDataSource {
        val submissions = mutableListOf<WasteRequest>()
        var nextOutcome: RemoteRequestOutcome? = null

        override suspend fun createRequest(
            authenticatedUid: String,
            request: WasteRequest
        ): RemoteRequestOutcome {
            submissions.add(request)
            return nextOutcome ?: RemoteRequestOutcome.Success(
                confirmedRequest = request.copy(requestRef = "WL-2611-TESTREF"),
                requestRef = "WL-2611-TESTREF",
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
