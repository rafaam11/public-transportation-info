package com.rafaam11.businfo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BusDatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BusDatabase::class.java,
    )

    @Test fun migration1To2PreservesFavoriteAndCreatesGeometryTable() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO favorites VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("MORNING", "route", "급행8-1", "0", "검단동 방면", "stop", "효동초등학교건너"),
            )
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT routeNo FROM favorites WHERE slot = 'MORNING'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("급행8-1", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM route_geometries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun migration2To3PreservesFavoriteAndAddsNullableRouteTypes() {
        helper.createDatabase("migration-2-3", 2).apply {
            execSQL(
                "INSERT INTO favorites VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("MORNING", "route", "급행8-1", "0", "유곡리 방면", "stop", "진천역"),
            )
            close()
        }
        helper.runMigrationsAndValidate("migration-2-3", 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT routeTypeCode FROM routes").use { cursor ->
                assertEquals("routeTypeCode", cursor.getColumnName(0))
            }
            db.query("SELECT routeTypeCode FROM favorites WHERE slot='MORNING'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
    }

    @Test fun migration3To4ClearsCommuteRowsAndPreservesReusableCaches() {
        helper.createDatabase("migration-3-4", 3).apply {
            execSQL(
                "INSERT INTO routes VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("route", "814", "대구대", "범물동", "범물동 방면", "대구대 방면", "1"),
            )
            execSQL(
                "INSERT INTO favorites VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf("MORNING", "route", "814", "0", "범물동 방면", "stop", "진천역", "1"),
            )
            execSQL("INSERT INTO arrival_snapshots VALUES (?, ?, ?)", arrayOf<Any>("MORNING", "[]", 100L))
            execSQL("INSERT INTO route_geometries VALUES (?, ?, ?, ?)", arrayOf<Any>("route", "0", "[]", 100L))
            close()
        }

        helper.runMigrationsAndValidate("migration-3-4", 4, true, MIGRATION_3_4).use { db ->
            assertEquals(0, db.count("favorites"))
            assertEquals(0, db.count("arrival_snapshots"))
            assertEquals(1, db.count("routes"))
            assertEquals(1, db.count("route_geometries"))
            assertEquals(0, db.count("favorite_stops"))
            assertEquals(0, db.count("widget_bindings"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
