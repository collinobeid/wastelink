package com.waste.link.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.repository.LocalWasteRequestRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migration test suite verifying database integrity across schema versions.
 *
 * Verifies Acceptance Criterion:
 * - P1B-16: Migration: Existing data survives the introduced migration (v1 -> v2).
 *
 * Historical Schema Note:
 * In commit `188b2a1`, `WasteLinkDatabase` was defined with `exportSchema = false` and had
 * table `offline_requests` without a `serviceZoneId` column. The migration safely adapts this
 * historical schema without fabricating a municipal service zone association (e.g. Kinondoni).
 * Unmapped historical records are explicitly assigned [WasteLinkMigrations.LEGACY_UNMAPPED_ZONE_ID]
 * and quarantined with syncStatus = 'SYNC_FAILED' so they cannot be mistakenly queued for sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WasteLinkMigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.filesDir, "migration_test_wastelink.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun p1b16_migration_v1_to_v2_existingDataSurvivesWithoutLoss() = runBlocking {
        // Step 1: Create version 1 database with legacy `offline_requests` schema
        val helperConfigV1 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `offline_requests` (
                            `requestRef` TEXT PRIMARY KEY NOT NULL,
                            `clientUid` TEXT NOT NULL,
                            `geohash` TEXT NOT NULL,
                            `lat` REAL NOT NULL,
                            `lng` REAL NOT NULL,
                            `pickupAddress` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `volumeTier` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `gatePhotoBase64` TEXT,
                            `pocPhotoBase64` TEXT,
                            `status` TEXT NOT NULL,
                            `createdAtMillis` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helperV1 = FrameworkSQLiteOpenHelperFactory().create(helperConfigV1)
        val dbV1 = helperV1.writableDatabase

        // Step 2: Insert historical test data into version 1
        dbV1.execSQL(
            """
            INSERT INTO `offline_requests` (
                `requestRef`, `clientUid`, `geohash`, `lat`, `lng`,
                `pickupAddress`, `category`, `volumeTier`, `notes`,
                `gatePhotoBase64`, `pocPhotoBase64`, `status`, `createdAtMillis`
            ) VALUES (
                'WL-2610-HIST01', 'resident_legacy_1', 'kygck5q', -6.7924, 39.2083,
                'Old Bagamoyo Rd 42', 'cat_household', 'standard_bin', 'Beware of dog',
                'data:image/webp;base64,gate_test_data', NULL, 'PENDING_DISPATCH', 1730700000000
            )
            """.trimIndent()
        )

        dbV1.execSQL(
            """
            INSERT INTO `offline_requests` (
                `requestRef`, `clientUid`, `geohash`, `lat`, `lng`,
                `pickupAddress`, `category`, `volumeTier`, `notes`,
                `gatePhotoBase64`, `pocPhotoBase64`, `status`, `createdAtMillis`
            ) VALUES (
                'WL-2610-HIST02', 'resident_legacy_2', 'kygck5q', -6.7900, 39.2000,
                'Samora Ave 10', 'cat_recyclables', 'small_bag', 'Left on porch',
                NULL, NULL, 'PENDING_DISPATCH', 1730700050000
            )
            """.trimIndent()
        )

        // Close V1 database
        dbV1.close()
        helperV1.close()

        // Step 3: Run MIGRATION_1_2 on the database
        val helperConfigV2 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {}

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 1 && newVersion == 2) {
                        WasteLinkMigrations.MIGRATION_1_2.migrate(db)
                    }
                }
            })
            .build()

        val helperV2 = FrameworkSQLiteOpenHelperFactory().create(helperConfigV2)
        val dbV2 = helperV2.writableDatabase

        // Step 4: Verify that tables exist
        val tablesCursor = dbV2.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableSetOf<String>()
        while (tablesCursor.moveToNext()) {
            tableNames.add(tablesCursor.getString(0))
        }
        tablesCursor.close()

        assertTrue("Table 'local_waste_requests' must exist", tableNames.contains("local_waste_requests"))
        assertTrue("Table 'resident_drafts' must exist", tableNames.contains("resident_drafts"))

        // Step 5: Verify that the legacy data survived and migrated into `local_waste_requests`
        val cursor = dbV2.query("SELECT * FROM `local_waste_requests` ORDER BY `createdAtEpochMs` ASC")
        assertEquals("Both legacy records must be migrated", 2, cursor.count)

        assertTrue(cursor.moveToFirst())
        // Record 1
        val id1 = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val clientUid1 = cursor.getString(cursor.getColumnIndexOrThrow("clientUid"))
        val requestRef1 = cursor.getString(cursor.getColumnIndexOrThrow("requestRef"))
        val lat1 = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"))
        val lng1 = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"))
        val geohash1 = cursor.getString(cursor.getColumnIndexOrThrow("geohash"))
        val serviceZone1 = cursor.getString(cursor.getColumnIndexOrThrow("serviceZoneId"))
        val pickup1 = cursor.getString(cursor.getColumnIndexOrThrow("pickupAddress"))
        val cat1 = cursor.getString(cursor.getColumnIndexOrThrow("categoryId"))
        val vol1 = cursor.getString(cursor.getColumnIndexOrThrow("volumeTierId"))
        val notes1 = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
        val gateB64_1 = cursor.getString(cursor.getColumnIndexOrThrow("gatePhotoBase64"))
        val status1 = cursor.getString(cursor.getColumnIndexOrThrow("status"))
        val syncStatus1 = cursor.getString(cursor.getColumnIndexOrThrow("syncStatus"))
        val syncErrorMessage1 = cursor.getString(cursor.getColumnIndexOrThrow("syncErrorMessage"))
        val createdAt1 = cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMs"))
        val currency1 = cursor.getString(cursor.getColumnIndexOrThrow("currency"))

        assertEquals("WL-2610-HIST01", id1)
        assertEquals("resident_legacy_1", clientUid1)
        assertEquals("WL-2610-HIST01", requestRef1)
        assertEquals(-6.7924, lat1, 0.0001)
        assertEquals(39.2083, lng1, 0.0001)
        assertEquals("kygck5q", geohash1)
        assertEquals(WasteLinkMigrations.LEGACY_UNMAPPED_ZONE_ID, serviceZone1)
        assertNotEquals("Historical record must NOT be assigned to Kinondoni", "zone_kinondoni", serviceZone1)
        assertEquals("Old Bagamoyo Rd 42", pickup1)
        assertEquals("cat_household", cat1)
        assertEquals("standard_bin", vol1)
        assertEquals("Beware of dog", notes1)
        assertEquals("data:image/webp;base64,gate_test_data", gateB64_1)
        assertEquals("PENDING_DISPATCH", status1)
        assertEquals("SYNC_FAILED", syncStatus1)
        assertEquals(WasteLinkMigrations.LEGACY_UNMAPPED_SYNC_ERROR, syncErrorMessage1)
        assertEquals(1730700000000L, createdAt1)
        assertEquals("TZS", currency1)

        // Record 2
        assertTrue(cursor.moveToNext())
        val id2 = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val clientUid2 = cursor.getString(cursor.getColumnIndexOrThrow("clientUid"))
        val serviceZone2 = cursor.getString(cursor.getColumnIndexOrThrow("serviceZoneId"))
        val syncStatus2 = cursor.getString(cursor.getColumnIndexOrThrow("syncStatus"))
        val syncErrorMessage2 = cursor.getString(cursor.getColumnIndexOrThrow("syncErrorMessage"))

        assertEquals("WL-2610-HIST02", id2)
        assertEquals("resident_legacy_2", clientUid2)
        assertEquals(WasteLinkMigrations.LEGACY_UNMAPPED_ZONE_ID, serviceZone2)
        assertNotEquals("Historical record must NOT be assigned to Kinondoni", "zone_kinondoni", serviceZone2)
        assertEquals("SYNC_FAILED", syncStatus2)
        assertEquals(WasteLinkMigrations.LEGACY_UNMAPPED_SYNC_ERROR, syncErrorMessage2)

        cursor.close()
        dbV2.close()
        helperV2.close()

        // Step 6: Verify via Room and LocalWasteRequestRepository that unmapped requests cannot be queued for sync
        val roomDb = Room.databaseBuilder(context, WasteLinkDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repository = LocalWasteRequestRepositoryImpl(
            database = roomDb,
            draftDao = roomDb.residentDraftDao(),
            requestDao = roomDb.localWasteRequestDao()
        )

        val queuedLegacy1 = repository.getQueuedRequests("resident_legacy_1")
        assertTrue(
            "Legacy unmapped record must NOT be returned in getQueuedRequests",
            queuedLegacy1.isEmpty()
        )

        val queuedLegacy2 = repository.getQueuedRequests("resident_legacy_2")
        assertTrue(
            "Legacy unmapped record must NOT be returned in getQueuedRequests",
            queuedLegacy2.isEmpty()
        )

        // Confirm that the records exist with SYNC_FAILED status
        val failedRecords = roomDb.localWasteRequestDao().getRequestsBySyncStatus(
            "resident_legacy_1",
            LocalSyncStatus.SYNC_FAILED
        )
        assertEquals(1, failedRecords.size)
        assertEquals(WasteLinkMigrations.LEGACY_UNMAPPED_ZONE_ID, failedRecords.first().serviceZoneId)
        assertEquals(WasteLinkMigrations.LEGACY_UNMAPPED_SYNC_ERROR, failedRecords.first().syncErrorMessage)

        roomDb.close()
    }

    @Test
    fun p1b16_migration_v1_to_v2_withoutLegacyTable_createsNewTablesCleanly() {
        val helperConfigV2 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    WasteLinkMigrations.MIGRATION_1_2.migrate(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    WasteLinkMigrations.MIGRATION_1_2.migrate(db)
                }
            })
            .build()

        val helperV2 = FrameworkSQLiteOpenHelperFactory().create(helperConfigV2)
        val dbV2 = helperV2.writableDatabase

        val cursor = dbV2.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue(tableNames.contains("local_waste_requests"))
        assertTrue(tableNames.contains("resident_drafts"))

        dbV2.close()
        helperV2.close()
    }
}
