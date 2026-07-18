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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `favorites`")
        db.execSQL("DELETE FROM `arrival_snapshots`")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `stops` (
                `stopId` TEXT NOT NULL,
                `stopName` TEXT NOT NULL,
                `longitude` REAL NOT NULL,
                `latitude` REAL NOT NULL,
                PRIMARY KEY(`stopId`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stops_stopName` ON `stops` (`stopName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stops_longitude_latitude` ON `stops` (`longitude`, `latitude`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `favorite_stops` (
                `id` TEXT NOT NULL,
                `stopId` TEXT NOT NULL,
                `stopName` TEXT NOT NULL,
                `longitude` REAL NOT NULL,
                `latitude` REAL NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_stops_stopId` ON `favorite_stops` (`stopId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_stops_sortOrder` ON `favorite_stops` (`sortOrder`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `favorite_pinned_routes` (
                `favoriteStopId` TEXT NOT NULL,
                `routeId` TEXT NOT NULL,
                `moveDirection` TEXT NOT NULL,
                `routeNo` TEXT NOT NULL,
                `directionLabel` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `routeTypeCode` TEXT,
                PRIMARY KEY(`favoriteStopId`, `routeId`, `moveDirection`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_pinned_routes_favoriteStopId` ON `favorite_pinned_routes` (`favoriteStopId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_pinned_routes_favoriteStopId_sortOrder` ON `favorite_pinned_routes` (`favoriteStopId`, `sortOrder`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `stop_arrival_snapshots` (
                `stopId` TEXT NOT NULL,
                `groupsJson` TEXT NOT NULL,
                `fetchedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`stopId`)
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `widget_bindings` (
                `appWidgetId` INTEGER NOT NULL,
                `favoriteStopId` TEXT NOT NULL,
                `configuredAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`appWidgetId`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_widget_bindings_favoriteStopId` ON `widget_bindings` (`favoriteStopId`)")
    }
}
