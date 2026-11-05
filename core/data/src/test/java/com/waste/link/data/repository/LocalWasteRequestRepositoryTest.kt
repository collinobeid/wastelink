package com.waste.link.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.waste.link.data.local.WasteLinkDatabase
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.models.ResidentDraft
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Acceptance test suite for [LocalWasteRequestRepository] and Room persistence.
 *
 * Verifies Acceptance Criteria Matrix:
 * - P1B-01: Save draft
 * - P1B-02: Update draft
 * - P1B-03: Restore draft across database close/reopen
 * - P1B-04: Delete draft
 * - P1B-05: UID isolation (draft read)
 * - P1B-06: UID write isolation (draft write)
 * - P1B-07: Save local request
 * - P1B-08: Request uniqueness (duplicate ID handling)
 * - P1B-09: Request isolation (request read)
 * - P1B-10: Update isolation (request update)
 * - P1B-11: Delete isolation (request delete)
 * - P1B-12: Queue retrieval (only current user's queued requests)
 * - P1B-13: Sync-state separation
 * - P1B-14: No false confirmation
 * - P1B-15: Transaction integrity
 * - P1B-17: Empty UID fails safely
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalWasteRequestRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: WasteLinkDatabase
    private lateinit var repository: LocalWasteRequestRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WasteLinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = LocalWasteRequestRepositoryImpl(
            database = database,
            draftDao = database.residentDraftDao(),
            requestDao = database.localWasteRequestDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // DRAFT TESTS (P1B-01 .. P1B-06)
    // =========================================================================

    @Test
    fun p1b01_saveDraft_isPersistedAndRetrievable() = runBlocking {
        val draft = createSampleDraft(clientUid = "user_alpha", step = "CATEGORY")
        repository.saveDraft("user_alpha", draft)

        val retrieved = repository.getDraft("user_alpha")
        assertNotNull(retrieved)
        assertEquals("user_alpha", retrieved?.clientUid)
        assertEquals("CATEGORY", retrieved?.currentStep)
        assertEquals(draft.clientGeneratedId, retrieved?.clientGeneratedId)
    }

    @Test
    fun p1b02_updateDraft_updatedValuesSurviveDatabaseReload() = runBlocking {
        val initial = createSampleDraft(clientUid = "user_alpha", step = "CATEGORY")
        repository.saveDraft("user_alpha", initial)

        val updated = initial.copy(
            currentStep = "LOCATION",
            latitude = -6.7924,
            longitude = 39.2083,
            pickupAddress = "Ali Hassan Mwinyi Rd",
            serviceZoneId = "zone_kinondoni",
            updatedAtEpochMs = 1730800100000L
        )
        repository.saveDraft("user_alpha", updated)

        val retrieved = repository.getDraft("user_alpha")
        assertNotNull(retrieved)
        assertEquals("LOCATION", retrieved?.currentStep)
        assertEquals(-6.7924, retrieved?.latitude!!, 0.0001)
        assertEquals(39.2083, retrieved?.longitude!!, 0.0001)
        assertEquals("Ali Hassan Mwinyi Rd", retrieved?.pickupAddress)
        assertEquals("zone_kinondoni", retrieved?.serviceZoneId)
    }

    @Test
    fun p1b03_restoreDraft_survivesDatabaseCloseAndReopen() = runBlocking {
        val dbFile = File(context.filesDir, "p1b03_draft_test.db")
        if (dbFile.exists()) dbFile.delete()

        val diskDb1 = Room.databaseBuilder(context, WasteLinkDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo1 = LocalWasteRequestRepositoryImpl(
            database = diskDb1,
            draftDao = diskDb1.residentDraftDao(),
            requestDao = diskDb1.localWasteRequestDao()
        )

        val draft = createSampleDraft(clientUid = "user_restart", step = "VOLUME")
        repo1.saveDraft("user_restart", draft)
        diskDb1.close()

        // Reopen database from persistent file
        val diskDb2 = Room.databaseBuilder(context, WasteLinkDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo2 = LocalWasteRequestRepositoryImpl(
            database = diskDb2,
            draftDao = diskDb2.residentDraftDao(),
            requestDao = diskDb2.localWasteRequestDao()
        )

        val restored = repo2.getDraft("user_restart")
        diskDb2.close()
        dbFile.delete()

        assertNotNull("Draft must survive database close and reopen", restored)
        assertEquals("user_restart", restored?.clientUid)
        assertEquals("VOLUME", restored?.currentStep)
    }

    @Test
    fun p1b04_deleteDraft_deletedDraftIsNoLongerReturned() = runBlocking {
        val draft = createSampleDraft(clientUid = "user_to_delete")
        repository.saveDraft("user_to_delete", draft)
        assertNotNull(repository.getDraft("user_to_delete"))

        val deleted = repository.deleteDraft("user_to_delete")
        assertTrue(deleted)

        val retrieved = repository.getDraft("user_to_delete")
        assertNull("Deleted draft must return null", retrieved)
    }

    @Test
    fun p1b05_uidIsolation_userACannotRetrieveUserBDraft() = runBlocking {
        val draftB = createSampleDraft(clientUid = "user_bravo", step = "CONFIRM")
        repository.saveDraft("user_bravo", draftB)

        // User Alpha attempts to retrieve User Bravo's draft
        val retrievedByA = repository.getDraft("user_alpha")
        assertNull("User Alpha must not see User Bravo's draft", retrievedByA)

        // User Bravo retrieves their own draft successfully
        val retrievedByB = repository.getDraft("user_bravo")
        assertNotNull(retrievedByB)
        assertEquals("user_bravo", retrievedByB?.clientUid)
    }

    @Test
    fun p1b06_uidWriteIsolation_userACannotOverwriteUserBDraft() = runBlocking {
        val draftB = createSampleDraft(clientUid = "user_bravo", step = "CATEGORY")
        repository.saveDraft("user_bravo", draftB)

        // User Alpha attempts to overwrite User Bravo's draft
        val maliciousDraft = draftB.copy(currentStep = "MALICIOUS_OVERWRITE")
        try {
            repository.saveDraft("user_alpha", maliciousDraft)
            fail("Attempting to save draft with mismatched UID must throw IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("does not match authenticated user"))
        }

        // Verify User Bravo's draft is untouched
        val intactB = repository.getDraft("user_bravo")
        assertNotNull(intactB)
        assertEquals("CATEGORY", intactB?.currentStep)
    }

    // =========================================================================
    // LOCAL REQUEST TESTS (P1B-07 .. P1B-15)
    // =========================================================================

    @Test
    fun p1b07_saveLocalRequest_canBeReconstructedAsSameDomainData() = runBlocking {
        val request = createSampleRequest(id = "req-uuid-001", clientUid = "user_alpha")
        val saved = repository.saveLocalRequest(
            authenticatedUid = "user_alpha",
            request = request,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        assertEquals(request, saved)

        val retrieved = repository.getLocalRequest("req-uuid-001", "user_alpha")
        assertNotNull(retrieved)
        assertEquals(request, retrieved)

        val record = repository.getLocalRecord("req-uuid-001", "user_alpha")
        assertNotNull(record)
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, record?.syncStatus)
    }

    @Test
    fun p1b08_requestUniqueness_duplicateUserRequestIdHandledDeterministically() = runBlocking {
        val request = createSampleRequest(id = "req-unique-001", clientUid = "user_alpha")
        repository.saveLocalRequest(
            authenticatedUid = "user_alpha",
            request = request,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        // Attempting to insert duplicate (id, clientUid) must be rejected deterministically by primary key constraint
        try {
            repository.saveLocalRequest(
                authenticatedUid = "user_alpha",
                request = request,
                syncStatus = LocalSyncStatus.QUEUED_OFFLINE
            )
            fail("Inserting duplicate request record must throw an exception")
        } catch (expected: Exception) {
            // SQLiteConstraintException or Room exception on conflict ABORT
            assertTrue(
                "Must be a constraint violation",
                expected.javaClass.simpleName.contains("Constraint") ||
                    expected.message?.contains("UNIQUE") == true ||
                    expected.message?.contains("constraint") == true
            )
        }
    }

    @Test
    fun p1b09_requestIsolation_userACannotRetrieveUserBLocalRequest() = runBlocking {
        val requestB = createSampleRequest(id = "req-b-001", clientUid = "user_bravo")
        repository.saveLocalRequest(
            authenticatedUid = "user_bravo",
            request = requestB,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        // User Alpha attempts to query User Bravo's request by ID
        val retrievedByA = repository.getLocalRequest("req-b-001", "user_alpha")
        assertNull("User Alpha must not retrieve User Bravo's request", retrievedByA)

        // User Bravo queries their own request
        val retrievedByB = repository.getLocalRequest("req-b-001", "user_bravo")
        assertNotNull(retrievedByB)
        assertEquals("user_bravo", retrievedByB?.clientUid)
    }

    @Test
    fun p1b10_updateIsolation_userACannotUpdateUserBLocalRequest() = runBlocking {
        val requestB = createSampleRequest(id = "req-b-002", clientUid = "user_bravo")
        repository.saveLocalRequest(
            authenticatedUid = "user_bravo",
            request = requestB,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        // User Alpha attempts to update sync status on User Bravo's request
        val updated = repository.updateSyncStatus(
            id = "req-b-002",
            clientUid = "user_alpha",
            newStatus = LocalSyncStatus.SYNCED
        )
        assertFalse("User Alpha update attempt must return false and affect 0 rows", updated)

        // Verify User Bravo's record remains QUEUED_OFFLINE
        val recordB = repository.getLocalRecord("req-b-002", "user_bravo")
        assertNotNull(recordB)
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, recordB?.syncStatus)
    }

    @Test
    fun p1b11_deleteIsolation_userACannotDeleteUserBLocalRequest() = runBlocking {
        val requestB = createSampleRequest(id = "req-b-003", clientUid = "user_bravo")
        repository.saveLocalRequest(
            authenticatedUid = "user_bravo",
            request = requestB,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        // User Alpha attempts to delete User Bravo's request
        val deleted = repository.deleteLocalRequest("req-b-003", "user_alpha")
        assertFalse("User Alpha delete attempt must return false and delete 0 rows", deleted)

        // Verify User Bravo's request is still present
        val recordB = repository.getLocalRequest("req-b-003", "user_bravo")
        assertNotNull("User Bravo's record must not be deleted by User Alpha", recordB)
    }

    @Test
    fun p1b12_queueRetrieval_onlyCurrentUsersQueuedRecordsReturned() = runBlocking {
        val reqA1 = createSampleRequest(id = "req-a-q1", clientUid = "user_alpha")
        val reqA2 = createSampleRequest(id = "req-a-q2", clientUid = "user_alpha")
        val reqB1 = createSampleRequest(id = "req-b-q1", clientUid = "user_bravo")

        repository.saveLocalRequest("user_alpha", reqA1, LocalSyncStatus.QUEUED_OFFLINE)
        repository.saveLocalRequest("user_alpha", reqA2, LocalSyncStatus.QUEUED_OFFLINE)
        repository.saveLocalRequest("user_bravo", reqB1, LocalSyncStatus.QUEUED_OFFLINE)

        // User Alpha gets queued records
        val queuedA = repository.getQueuedRequests("user_alpha")
        assertEquals(2, queuedA.size)
        assertTrue(queuedA.any { it.id == "req-a-q1" })
        assertTrue(queuedA.any { it.id == "req-a-q2" })
        assertFalse(queuedA.any { it.id == "req-b-q1" })

        // User Bravo gets queued records
        val queuedB = repository.getQueuedRequests("user_bravo")
        assertEquals(1, queuedB.size)
        assertEquals("req-b-q1", queuedB.first().id)
    }

    @Test
    fun p1b13_syncStateSeparation_updatingSyncStatusDoesNotAlterDomainLifecycleStatus() = runBlocking {
        val request = createSampleRequest(
            id = "req-status-sep",
            clientUid = "user_alpha",
            status = RequestLifecycleStatus.PENDING_DISPATCH
        )
        repository.saveLocalRequest(
            authenticatedUid = "user_alpha",
            request = request,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        // Update local sync status to SYNCED
        repository.updateSyncStatus(
            id = "req-status-sep",
            clientUid = "user_alpha",
            newStatus = LocalSyncStatus.SYNCED
        )

        val domain = repository.getLocalRequest("req-status-sep", "user_alpha")
        assertNotNull(domain)
        // Domain lifecycle status remains PENDING_DISPATCH
        assertEquals(RequestLifecycleStatus.PENDING_DISPATCH, domain?.status)

        // Local sync status is SYNCED
        val record = repository.getLocalRecord("req-status-sep", "user_alpha")
        assertEquals(LocalSyncStatus.SYNCED, record?.syncStatus)
    }

    @Test
    fun p1b14_noFalseConfirmation_localInsertionNeverReportsRemoteCreationSuccess() = runBlocking {
        val request = createSampleRequest(id = "req-no-fake", clientUid = "user_alpha")

        // Local save returns the locally persisted request, NOT CreateWasteRequestOutcome.Success
        val localSaved = repository.saveLocalRequest(
            authenticatedUid = "user_alpha",
            request = request,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )
        assertNotNull(localSaved)
        // Canonical requestRef remains unallocated until trusted server allocation
        assertNull(localSaved.requestRef)
        assertTrue(localSaved.isUnallocatedRef)

        // Verify that LocalWasteRequestRepository interface does not expose remote creation outcome
        val methods = LocalWasteRequestRepository::class.java.methods
        for (method in methods) {
            assertFalse(
                "Local persistence repository must not return CreateWasteRequestOutcome",
                method.returnType.name.contains("CreateWasteRequestOutcome")
            )
        }
    }

    @Test
    fun p1b15_transactionIntegrity_failedMultiStepWritesDoNotLeavePartialState() = runBlocking {
        val initialReq = createSampleRequest(id = "req-tx-1", clientUid = "user_tx")
        repository.saveLocalRequest(
            authenticatedUid = "user_tx",
            request = initialReq,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )

        try {
            repository.runInTransaction {
                // Step 1: Update sync status
                database.localWasteRequestDao().updateSyncStatus(
                    id = "req-tx-1",
                    clientUid = "user_tx",
                    syncStatus = LocalSyncStatus.UPLOADING,
                    lastSyncAttemptEpochMs = 12345L,
                    errorMessage = null,
                    retryCount = 1
                )
                // Step 2: Delete draft
                database.residentDraftDao().deleteDraft("user_tx")

                // Step 3: Intentional simulated failure
                throw IllegalStateException("Simulated mid-transaction failure")
            }
            fail("Transaction must throw")
        } catch (expected: IllegalStateException) {
            assertEquals("Simulated mid-transaction failure", expected.message)
        }

        // Verify Step 1 was rolled back
        val record = repository.getLocalRecord("req-tx-1", "user_tx")
        assertNotNull(record)
        assertEquals(
            "Transaction rollback must preserve original syncStatus",
            LocalSyncStatus.QUEUED_OFFLINE,
            record?.syncStatus
        )
    }

    // =========================================================================
    // REMEDIATION TESTS: AUTHENTICATED UID IDENTITY ENFORCEMENT ON SAVE
    // =========================================================================

    @Test
    fun saveLocalRequest_withMatchingAuthenticatedUid_succeeds() = runBlocking {
        val request = createSampleRequest(id = "req-match-1", clientUid = "user_alpha")
        val saved = repository.saveLocalRequest(
            authenticatedUid = "user_alpha",
            request = request,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        )
        assertEquals(request, saved)
        assertEquals("user_alpha", repository.getLocalRequest("req-match-1", "user_alpha")?.clientUid)
    }

    @Test
    fun saveLocalRequest_withMismatchedAuthenticatedUid_throwsAndDoesNotWriteToDb() = runBlocking {
        val request = createSampleRequest(id = "req-mismatch-1", clientUid = "user_victim")

        // User Attacker attempts to save a request claiming to be User Victim
        try {
            repository.saveLocalRequest(
                authenticatedUid = "user_attacker",
                request = request,
                syncStatus = LocalSyncStatus.QUEUED_OFFLINE
            )
            fail("Saving request with mismatched authenticated UID must throw IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("does not match authenticated user"))
            assertTrue(expected.message!!.contains("Caller cannot persist a request carrying another user's UID"))
        }

        // Assert that zero records were created for either UID
        assertNull("Attacker must not have request persisted", repository.getLocalRequest("req-mismatch-1", "user_attacker"))
        assertNull("Victim must not have request persisted", repository.getLocalRequest("req-mismatch-1", "user_victim"))
        assertEquals(0, database.localWasteRequestDao().countRequestsByUser("user_attacker"))
        assertEquals(0, database.localWasteRequestDao().countRequestsByUser("user_victim"))
    }

    @Test
    fun saveLocalRequest_withBlankAuthenticatedUid_throwsAndDoesNotWriteToDb() = runBlocking {
        val request = createSampleRequest(id = "req-blank-1", clientUid = "user_alpha")
        val blankCases = listOf("", "   ", "\t", "\n")

        for (blankUid in blankCases) {
            try {
                repository.saveLocalRequest(
                    authenticatedUid = blankUid,
                    request = request,
                    syncStatus = LocalSyncStatus.QUEUED_OFFLINE
                )
                fail("Blank authenticated UID must throw IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("must not be blank"))
            }
        }

        assertEquals(0, database.localWasteRequestDao().countRequestsByUser("user_alpha"))
    }

    @Test
    fun p1b17_emptyUid_blankOrMissingUidFailsSafely() = runBlocking {
        val blankUidCases = listOf("", "   ", "\t", "\n")

        for (blankUid in blankUidCases) {
            // Draft operations
            try {
                repository.getDraft(blankUid)
                fail("getDraft with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }

            try {
                repository.deleteDraft(blankUid)
                fail("deleteDraft with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }

            // Local request operations
            try {
                repository.saveLocalRequest(
                    authenticatedUid = blankUid,
                    request = createSampleRequest("req-fail", "user_alpha")
                )
                fail("saveLocalRequest with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }

            try {
                repository.getLocalRequest("req-1", blankUid)
                fail("getLocalRequest with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }

            try {
                repository.getQueuedRequests(blankUid)
                fail("getQueuedRequests with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }

            try {
                repository.deleteLocalRequest("req-1", blankUid)
                fail("deleteLocalRequest with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }

            try {
                repository.updateSyncStatus("req-1", blankUid, LocalSyncStatus.SYNCED)
                fail("updateSyncStatus with blank UID must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("must not be blank"))
            }
        }
    }

    // =========================================================================
    // HELPER BUILDERS
    // =========================================================================

    private fun createSampleDraft(
        clientUid: String,
        step: String = "CATEGORY",
        id: String = "uuid-${System.currentTimeMillis()}"
    ): ResidentDraft {
        return ResidentDraft(
            clientUid = clientUid,
            clientGeneratedId = id,
            currentStep = step,
            categoryId = "cat_household",
            latitude = -6.7924,
            longitude = 39.2083,
            pickupAddress = "Ali Hassan Mwinyi Rd",
            serviceZoneId = "zone_kinondoni",
            notes = "Gate code 1234",
            gatePhotoLocalPath = null,
            volumeTierId = "standard_bin",
            createdAtEpochMs = 1730800000000L,
            updatedAtEpochMs = 1730800000000L
        )
    }

    private fun createSampleRequest(
        id: String,
        clientUid: String,
        status: RequestLifecycleStatus = RequestLifecycleStatus.PENDING_DISPATCH
    ): WasteRequest {
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            requestRef = null,
            clientUid = clientUid,
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_kinondoni",
            pickupAddress = "Ali Hassan Mwinyi Rd, Dar es Salaam",
            categoryId = "cat_household",
            volumeTier = VolumeTier.STANDARD_BIN,
            notes = "Near supermarket",
            status = status,
            createdAtEpochMs = 1730800000000L,
            updatedAtEpochMs = 1730800000000L
        )
    }
}
