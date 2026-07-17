package com.rafaam11.businfo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `route_geometries` (
                `routeId` TEXT NOT NULL,
                `moveDirection` TEXT NOT NULL,
                `segmentsJson` TEXT NOT NULL,
                `fetchedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`routeId`, `moveDirection`)
            )""".trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routes` ADD COLUMN `routeTypeCode` TEXT")
        db.execSQL("ALTER TABLE `favorites` ADD COLUMN `routeTypeCode` TEXT")
    }
}
