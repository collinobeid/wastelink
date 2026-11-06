package com.waste.link.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
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

/**
 * Verified integration test using Android WorkManager's [TestListenableWorkerBuilder]
 * to prove actual [WasteRequestSyncWorker] execution, failed request retry selection,
 * server timestamp persistence, and terminal failure limits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WasteRequestWorkManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: WasteLinkDatabase
    private lateinit var localRepository: LocalWasteRequestRepository
    private lateinit var fakeRemoteDataSource: TestFakeRemoteDataSource
    private lateinit var fakeSessionProvider: TestFakeSessionProvider
    private lateinit var workManagerScheduler: WorkManagerWasteRequestSyncScheduler
    private lateinit var orchestrator: WasteRequestSyncOrchestrator
    private lateinit var workerFactory: WorkerFactory

    private val authUser = User(
        id = "resident_wm_1",
        fullName = "Test Resident",
        email = "test@wastelink.tz",
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
        fakeRemoteDataSource = TestFakeRemoteDataSource()
        fakeSessionProvider = TestFakeSessionProvider(
            SessionState.Authenticated(user = authUser, isEmailVerified = true)
        )
        workManagerScheduler = WorkManagerWasteRequestSyncScheduler(context)
        orchestrator = WasteRequestSyncOrchestrator(
            localRepository = localRepository,
            remoteDataSource = fakeRemoteDataSource,
            sessionStateProvider = fakeSessionProvider,
            syncScheduler = workManagerScheduler
        )

        workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return if (workerClassName == WasteRequestSyncWorker::class.java.name) {
                    WasteRequestSyncWorker(
                        appContext,
                        workerParameters,
                        orchestrator,
                        fakeSessionProvider
                    )
                } else null
            }
        }

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(androidx.work.testing.SynchronousExecutor())
            .setWorkerFactory(workerFactory)
            .build()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleRequest(id: String = "wm_req_1"): WasteRequest {
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            requestRef = "WL-2611-WMTEST",
            clientUid = "resident_wm_1",
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            serviceZoneId = "zone_kinondoni",
            pickupAddress = "Masaki, Dar es Salaam",
            categoryId = "cat_general",
            volumeTier = VolumeTier.STANDARD_BIN
        )
    }

    @Test
    fun completeWorkManagerRetryFlow_transientFailure_thenWorkerExecutesAndSucceeds() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_wm_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        var testTime = 1793869200000L
        orchestrator.clock = { testTime }

        // Step 1: Initial sync attempt encounters transient network failure
        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.TransientFailure(
            cause = IOException("Network unreachable")
        )

        val firstResult = orchestrator.synchronizeSingleRequest("resident_wm_1", request.id)
        assertTrue(firstResult is SyncResult.QueuedOffline)

        // Finding 1: Record remains in QUEUED_OFFLINE with persisted retry count so worker can select it
        val recordAfterFirstFail = localRepository.getLocalRecord(request.id, "resident_wm_1")
        assertNotNull(recordAfterFirstFail)
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, recordAfterFirstFail!!.syncStatus)
        assertEquals(1, recordAfterFirstFail.retryCount)

        // Step 2: Time elapses past backoff window, network recovers, and WorkManager triggers worker
        testTime += 5_000L // 5 seconds later (> 3000ms backoff)
        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.Success(
            confirmedRequest = request.copy(requestRef = "WL-2611-WMTEST"),
            requestRef = "WL-2611-WMTEST",
            serverCreatedAtEpochMs = 1793869200000L,
            serverUpdatedAtEpochMs = 1793869200000L
        )

        // Build and execute the real WasteRequestSyncWorker via TestListenableWorkerBuilder
        val worker = TestListenableWorkerBuilder<WasteRequestSyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        val workerResult = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), workerResult)

        // Verify that the worker selected the failed record and synchronized it to SYNCED with authoritative server timestamps
        val recordAfterWorker = localRepository.getLocalRecord(request.id, "resident_wm_1")
        assertNotNull(recordAfterWorker)
        assertEquals(LocalSyncStatus.SYNCED, recordAfterWorker!!.syncStatus)
        assertEquals("WL-2611-WMTEST", recordAfterWorker.requestRef)
        assertEquals(1793869200000L, recordAfterWorker.createdAtEpochMs)
        assertEquals(1793869200000L, recordAfterWorker.updatedAtEpochMs)
    }

    @Test
    fun completeWorkManagerRetryFlow_exhaustedRetries_transitionsToTerminalFailure() = runBlocking {
        val request = sampleRequest()
        localRepository.saveLocalRequest("resident_wm_1", request, LocalSyncStatus.QUEUED_OFFLINE)

        // Pre-set retry count to MAX_RETRIES (3)
        localRepository.updateSyncStatus(
            id = request.id,
            clientUid = "resident_wm_1",
            newStatus = LocalSyncStatus.QUEUED_OFFLINE,
            retryCount = 3
        )

        // Fail again on next attempt
        fakeRemoteDataSource.nextOutcome = RemoteRequestOutcome.TransientFailure(
            cause = IOException("Persistent drop")
        )

        val result = orchestrator.synchronizeSingleRequest("resident_wm_1", request.id)
        assertTrue(result is SyncResult.Failure)

        // Record must transition permanently to SYNC_FAILED with retryCount = 4
        val record = localRepository.getLocalRecord(request.id, "resident_wm_1")
        assertNotNull(record)
        assertEquals(LocalSyncStatus.SYNC_FAILED, record!!.syncStatus)
        assertEquals(4, record.retryCount)

        // Ensure no queued records remain eligible for worker selection
        val queuedRecords = localRepository.getQueuedRecords("resident_wm_1")
        assertTrue(queuedRecords.isEmpty())
    }

    private class TestFakeSessionProvider(
        initialState: SessionState
    ) : SessionStateProvider {
        val mutableSessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()
    }

    private class TestFakeRemoteDataSource : WasteRequestRemoteDataSource {
        var nextOutcome: RemoteRequestOutcome? = null

        override suspend fun createRequest(
            authenticatedUid: String,
            request: WasteRequest
        ): RemoteRequestOutcome {
            return nextOutcome ?: RemoteRequestOutcome.Success(
                confirmedRequest = request,
                requestRef = request.requestRef ?: "WL-2611-WMTEST",
                serverCreatedAtEpochMs = 1793869200000L,
                serverUpdatedAtEpochMs = 1793869200000L
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
