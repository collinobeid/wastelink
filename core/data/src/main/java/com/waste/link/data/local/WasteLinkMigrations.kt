package com.waste.link.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Authoritative database migrations for [WasteLinkDatabase].
 *
 * Rules:
 * - Real explicit migrations. Destructive migration fallback is strictly prohibited.
 * - Existing user data from version 1 (table `offline_requests`) is preserved and migrated to
 *   `local_waste_requests` without loss.
 */
object WasteLinkMigrations {

    /**
     * Explicit unmapped service zone sentinel for migrated historical records.
     * Prevents fabricating a default municipal service zone (e.g. Kinondoni) when no zone
     * was recorded in legacy schemas.
     */
    const val LEGACY_UNMAPPED_ZONE_ID = "legacy_unmapped_zone"

    /**
     * Diagnostic error explanation for historical records with unmapped service zones.
     */
    const val LEGACY_UNMAPPED_SYNC_ERROR =
        "Legacy historical record has unmapped serviceZoneId; requires zone resolution before synchronization"

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create local_waste_requests table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_waste_requests` (
                    `id` TEXT NOT NULL,
                    `clientGeneratedId` TEXT NOT NULL,
                    `clientUid` TEXT NOT NULL,
                    `requestRef` TEXT,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `geohash` TEXT NOT NULL,
                    `serviceZoneId` TEXT NOT NULL,
                    `pickupAddress` TEXT NOT NULL,
                    `categoryId` TEXT NOT NULL,
                    `volumeTierId` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `gatePhotoUrl` TEXT,
                    `gatePhotoLocalPath` TEXT,
                    `gatePhotoBase64` TEXT,
                    `pocPhotoUrl` TEXT,
                    `pocPhotoBase64` TEXT,
                    `status` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `assignedCollectorUid` TEXT,
                    `assignedVehicleId` TEXT,
                    `priceTzs` INTEGER,
                    `currency` TEXT NOT NULL,
                    `residentPhoneNumber` TEXT,
                    `collectorPhoneNumber` TEXT,
                    `failureReason` TEXT,
                    `failurePhotoUrl` TEXT,
                    `failurePhotoBase64` TEXT,
                    `disputeReason` TEXT,
                    `adminResolutionNote` TEXT,
                    `createdAtEpochMs` INTEGER,
                    `updatedAtEpochMs` INTEGER,
                    `assignedAtEpochMs` INTEGER,
                    `enRouteAtEpochMs` INTEGER,
                    `arrivedAtEpochMs` INTEGER,
                    `collectedAtEpochMs` INTEGER,
                    `completedAtEpochMs` INTEGER,
                    `failedAtEpochMs` INTEGER,
                    `lastSyncAttemptEpochMs` INTEGER,
                    `syncErrorMessage` TEXT,
                    `retryCount` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`, `clientUid`)
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_waste_requests_clientUid` ON `local_waste_requests` (`clientUid`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_waste_requests_clientUid_syncStatus` ON `local_waste_requests` (`clientUid`, `syncStatus`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_waste_requests_clientUid_createdAtEpochMs` ON `local_waste_requests` (`clientUid`, `createdAtEpochMs`)")

            // 2. Create resident_drafts table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `resident_drafts` (
                    `clientUid` TEXT NOT NULL,
                    `clientGeneratedId` TEXT NOT NULL,
                    `currentStep` TEXT NOT NULL,
                    `categoryId` TEXT,
                    `latitude` REAL,
                    `longitude` REAL,
                    `pickupAddress` TEXT,
                    `serviceZoneId` TEXT,
                    `notes` TEXT,
                    `gatePhotoLocalPath` TEXT,
                    `volumeTierId` TEXT,
                    `createdAtEpochMs` INTEGER NOT NULL,
                    `updatedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`clientUid`)
                )
                """.trimIndent()
            )

            // 3. Migrate data from legacy table `offline_requests` if present
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='offline_requests'")
            val legacyTableExists = cursor.count > 0
            cursor.close()

            if (legacyTableExists) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `local_waste_requests` (
                        `id`, `clientGeneratedId`, `clientUid`, `requestRef`,
                        `latitude`, `longitude`, `geohash`, `serviceZoneId`,
                        `pickupAddress`, `categoryId`, `volumeTierId`, `notes`,
                        `gatePhotoUrl`, `gatePhotoLocalPath`, `gatePhotoBase64`,
                        `pocPhotoUrl`, `pocPhotoBase64`, `status`, `syncStatus`,
                        `assignedCollectorUid`, `assignedVehicleId`, `priceTzs`,
                        `currency`, `residentPhoneNumber`, `collectorPhoneNumber`,
                        `failureReason`, `failurePhotoUrl`, `failurePhotoBase64`,
                        `disputeReason`, `adminResolutionNote`, `createdAtEpochMs`,
                        `updatedAtEpochMs`, `assignedAtEpochMs`, `enRouteAtEpochMs`,
                        `arrivedAtEpochMs`, `collectedAtEpochMs`, `completedAtEpochMs`,
                        `failedAtEpochMs`, `lastSyncAttemptEpochMs`, `syncErrorMessage`,
                        `retryCount`
                    )
                    SELECT
                        requestRef AS id,
                        requestRef AS clientGeneratedId,
                        clientUid,
                        requestRef,
                        lat AS latitude,
                        lng AS longitude,
                        geohash,
                        '$LEGACY_UNMAPPED_ZONE_ID' AS serviceZoneId,
                        pickupAddress,
                        category AS categoryId,
                        volumeTier AS volumeTierId,
                        notes,
                        NULL AS gatePhotoUrl,
                        NULL AS gatePhotoLocalPath,
                        gatePhotoBase64,
                        NULL AS pocPhotoUrl,
                        pocPhotoBase64,
                        status,
                        'SYNC_FAILED' AS syncStatus,
                        NULL AS assignedCollectorUid,
                        NULL AS assignedVehicleId,
                        NULL AS priceTzs,
                        'TZS' AS currency,
                        NULL AS residentPhoneNumber,
                        NULL AS collectorPhoneNumber,
                        NULL AS failureReason,
                        NULL AS failurePhotoUrl,
                        NULL AS failurePhotoBase64,
                        NULL AS disputeReason,
                        NULL AS adminResolutionNote,
                        createdAtMillis AS createdAtEpochMs,
                        createdAtMillis AS updatedAtEpochMs,
                        NULL AS assignedAtEpochMs,
                        NULL AS enRouteAtEpochMs,
                        NULL AS arrivedAtEpochMs,
                        NULL AS collectedAtEpochMs,
                        NULL AS completedAtEpochMs,
                        NULL AS failedAtEpochMs,
                        NULL AS lastSyncAttemptEpochMs,
                        '$LEGACY_UNMAPPED_SYNC_ERROR' AS syncErrorMessage,
                        0 AS retryCount
                    FROM `offline_requests`
                    """.trimIndent()
                )
            }
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
}
