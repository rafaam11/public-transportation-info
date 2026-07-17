# Daegu Bus Realtime Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the one-shot vehicle detail list with a NAVER map that renders the selected commute direction's link-based route and confirmed GPS vehicle positions while hiding positions older than 30 seconds.

**Architecture:** Keep link geometry, live vehicle refresh, polling state, and NAVER SDK rendering behind separate interfaces. Assemble route links from `getLink02` and node coordinates from `getBasic02`, cache the resulting direction-specific segments in Room, and let a dedicated `RealtimeMapViewModel` poll `getPos02` only while the map is visible. Compose renders a testable map-and-bottom-sheet shell; a small Android adapter owns `MapView` lifecycle and NAVER overlays.

**Tech Stack:** Kotlin 2.4.10, Android API 26+, Jetpack Compose/Material 3, Navigation Compose, Coroutines/Flow, Room 2.8.4, OkHttp/Gson, NAVER Map Android SDK 3.23.3, JUnit 4, Compose UI tests.

## Global Constraints

- Show only the route direction saved on the selected commute card; never show the reverse direction.
- Vehicle markers use only confirmed `getPos02.xPos/yPos` coordinates; do not interpolate, extrapolate, or road-snap them.
- Freshness boundaries are exact: `0..15s` fresh, `>15..30s` delayed, and `>30s` hidden.
- A successful empty vehicle response immediately clears the visible marker/list state and means `현재 운행 차량 없음`.
- Poll only while the map destination is visible: 8 seconds after success, 15 seconds after the first transient failure, 30 seconds after later transient failures, and stop after authentication or quota errors.
- Never persist, display, or log `vhcNo2`, the Daegu service key, a complete authenticated request URL, or raw API responses.
- Use link/node data for route geometry; never bridge missing or disconnected links with an invented straight segment.
- Do not request Android location permission and do not display the user's current location.
- NAVER NCP Key ID comes only from ignored `local.properties` key `NAVER_MAP_NCP_KEY_ID`; the package registered in NAVER Cloud is `com.rafaam11.businfo` with Dynamic Map enabled.
- Do not run `connectedDebugAndroidTest` or another test that can replace/delete installed app data without explicit user approval in the active turn.

---

### Task 1: Add NAVER Map SDK and safe local configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: ignored root property `NAVER_MAP_NCP_KEY_ID` when present.
- Produces: `libs.naver.map`, manifest placeholder `naverMapNcpKeyId`, and a build that remains compilable with an empty local key.

- [ ] **Step 1: Write the failing configuration contract test**

Add this test to `FoundationContractTest`:

```kotlin
@Test
fun naverMapKeyIsInjectedWithoutACommittedLiteral() {
    val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
        if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
    }
    val catalog = File(repoRoot, "gradle/libs.versions.toml").readText()
    val appBuild = File(repoRoot, "app/build.gradle.kts").readText()
    val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText()

    assertTrue(catalog.contains("naverMap = \"3.23.3\""))
    assertTrue(catalog.contains("naver-map = { module = \"com.naver.maps:map-sdk\""))
    assertTrue(appBuild.contains("NAVER_MAP_NCP_KEY_ID"))
    assertTrue(appBuild.contains("implementation(libs.naver.map)"))
    assertTrue(manifest.contains("android:name=\"com.naver.maps.map.NCP_KEY_ID\""))
    assertTrue(manifest.contains("android:value=\"\${naverMapNcpKeyId}\""))
    assertFalse(manifest.contains("YOUR_NCP_KEY"))
    assertFalse(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
    assertFalse(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.FoundationContractTest.naverMapKeyIsInjectedWithoutACommittedLiteral
```

Expected: FAIL because the catalog, dependency, property loading, and manifest metadata do not exist.

- [ ] **Step 3: Add the SDK catalog entry and local-property injection**

Add to `gradle/libs.versions.toml`:

```toml
[versions]
naverMap = "3.23.3"

[libraries]
naver-map = { module = "com.naver.maps:map-sdk", version.ref = "naverMap" }
```

At the top of `app/build.gradle.kts`, add the import and property loader, then wire the placeholder and dependency:

```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val naverMapNcpKeyId = localProperties.getProperty("NAVER_MAP_NCP_KEY_ID").orEmpty()

android {
    defaultConfig {
        manifestPlaceholders["naverMapNcpKeyId"] = naverMapNcpKeyId
    }
}

dependencies {
    implementation(libs.naver.map)
}
```

Add under `<application>` in `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.naver.maps.map.NCP_KEY_ID"
    android:value="${naverMapNcpKeyId}" />
```

Add this non-secret setup note to `README.md`:

```markdown
## 네이버 지도 로컬 설정

NAVER Cloud Maps 애플리케이션에서 Dynamic Map을 활성화하고 Android 패키지
`com.rafaam11.businfo`를 등록한다. 발급된 NCP Key ID는 Git에 넣지 않고 프로젝트 루트의
ignored `local.properties`에 다음 이름으로만 저장한다.

`NAVER_MAP_NCP_KEY_ID=<발급된 Key ID>`

값이 없으면 APK는 빌드되지만 실기기 지도 인증은 실패 상태로 표시된다.
```

- [ ] **Step 4: Run the contract test and dependency build**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.FoundationContractTest.naverMapKeyIsInjectedWithoutACommittedLiteral :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Confirm `git grep -n -E "NAVER_MAP_NCP_KEY_ID=[A-Za-z0-9_-]{10,}" -- ':!docs/superpowers/plans/2026-07-17-daegu-bus-realtime-map.md'` returns no committed key-like value; documentation placeholders are allowed.

- [ ] **Step 5: Commit the SDK configuration**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt README.md
git commit -m "build: add Naver map SDK configuration"
```

---

### Task 2: Model and assemble disconnected-safe route geometry

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/RouteGeometry.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/domain/RouteGeometryAssemblerTest.kt`

**Interfaces:**
- Consumes: `RouteNode`, `RouteLink`, route ID, direction code, and fetch time.
- Produces: `GeoPoint`, `RouteSegment`, `RouteGeometry`, and `RouteGeometryAssembler.assemble(...)`.

- [ ] **Step 1: Write failing tests for link order, reversal, and discontinuities**

Create `RouteGeometryAssemblerTest.kt`:

```kotlin
package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGeometryAssemblerTest {
    private val nodes = listOf(
        RouteNode("a", 128.60, 35.80),
        RouteNode("b", 128.61, 35.81),
        RouteNode("c", 128.62, 35.82),
        RouteNode("x", 128.70, 35.90),
        RouteNode("y", 128.71, 35.91),
    )

    @Test fun `sorts links and reverses endpoints to preserve continuity`() {
        val result = RouteGeometryAssembler.assemble(
            "route", "0", nodes,
            listOf(
                RouteLink("route", "l2", "0", 2, "c", "b"),
                RouteLink("route", "l1", "0", 1, "a", "b"),
            ),
            Instant.EPOCH,
        )

        assertEquals(listOf("a", "b", "c"), result.segments.single().nodeIds)
    }

    @Test fun `splits disconnected links instead of drawing a bridge`() {
        val result = RouteGeometryAssembler.assemble(
            "route", "0", nodes,
            listOf(
                RouteLink("route", "l1", "0", 1, "a", "b"),
                RouteLink("route", "l2", "0", 2, "x", "y"),
            ),
            Instant.EPOCH,
        )

        assertEquals(listOf(listOf("a", "b"), listOf("x", "y")), result.segments.map { it.nodeIds })
    }

    @Test fun `drops links whose endpoint coordinates are missing`() {
        val result = RouteGeometryAssembler.assemble(
            "route", "0", nodes,
            listOf(RouteLink("route", "broken", "0", 1, "a", "missing")),
            Instant.EPOCH,
        )

        assertEquals(emptyList<RouteSegment>(), result.segments)
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.domain.RouteGeometryAssemblerTest
```

Expected: compilation FAIL because the route geometry types do not exist.

- [ ] **Step 3: Implement the pure geometry types and assembler**

Create `RouteGeometry.kt`:

```kotlin
package com.rafaam11.businfo.domain

import java.time.Instant

data class GeoPoint(val longitude: Double, val latitude: Double)
data class RouteNode(val nodeId: String, val longitude: Double, val latitude: Double)
data class RouteLink(
    val routeId: String,
    val linkId: String,
    val moveDirection: String,
    val sequence: Int,
    val startNodeId: String,
    val endNodeId: String,
)
data class RouteSegment(val nodeIds: List<String>, val points: List<GeoPoint>)
data class RouteGeometry(
    val routeId: String,
    val moveDirection: String,
    val segments: List<RouteSegment>,
    val fetchedAt: Instant,
)

object RouteGeometryAssembler {
    fun assemble(
        routeId: String,
        moveDirection: String,
        nodes: Collection<RouteNode>,
        links: Collection<RouteLink>,
        fetchedAt: Instant,
    ): RouteGeometry {
        val nodeById = nodes.associateBy(RouteNode::nodeId)
        val completed = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        links.asSequence()
            .filter { it.routeId == routeId && it.moveDirection == moveDirection }
            .sortedBy(RouteLink::sequence)
            .forEach { link ->
                if (nodeById[link.startNodeId] == null || nodeById[link.endNodeId] == null) return@forEach
                if (current.isEmpty()) {
                    current = mutableListOf(link.startNodeId, link.endNodeId)
                } else when (current.last()) {
                    link.startNodeId -> current += link.endNodeId
                    link.endNodeId -> current += link.startNodeId
                    else -> {
                        completed += current
                        current = mutableListOf(link.startNodeId, link.endNodeId)
                    }
                }
            }
        if (current.size >= 2) completed += current

        val segments = completed.filter { it.size >= 2 }.map { nodeIds ->
            RouteSegment(
                nodeIds = nodeIds,
                points = nodeIds.map { id -> nodeById.getValue(id).let { GeoPoint(it.longitude, it.latitude) } },
            )
        }
        return RouteGeometry(routeId, moveDirection, segments, fetchedAt)
    }
}
```

- [ ] **Step 4: Run the geometry tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.domain.RouteGeometryAssemblerTest
```

Expected: all 3 tests PASS.

- [ ] **Step 5: Commit the pure geometry unit**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/domain/RouteGeometry.kt app/src/test/kotlin/com/rafaam11/businfo/domain/RouteGeometryAssemblerTest.kt
git commit -m "feat: assemble link based route geometry"
```

---

### Task 3: Parse basic nodes and route links from the official API

**Files:**
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/remote/DaeguBusRemoteDataSource.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSourceTest.kt`

**Interfaces:**
- Consumes: verified `getBasic02` `items.node[]` and `getLink02` array contracts.
- Produces: `basicNodes(serviceKey): RemoteResult<List<RouteNode>>` and `routeLinks(serviceKey, routeId): RemoteResult<List<RouteLink>>`.

- [ ] **Step 1: Add failing MockWebServer tests**

Add tests following the existing `server.enqueue(...)` fixture style:

```kotlin
@Test fun `basic nodes parse coordinates without retaining unrelated fields`() = runTest {
    enqueueSuccess("""{"route":[],"bs":[],"node":[
      {"nodeId":"n1","nodeNm":"교차로","xPos":128.61,"yPos":35.81,"bsYn":"N"}
    ],"link":[]}""")

    val result = remote.basicNodes("key") as RemoteResult.Success

    assertEquals(listOf(RouteNode("n1", 128.61, 35.81)), result.value)
    assertEquals("/getBasic02", server.takeRequest().requestUrl!!.encodedPath)
}

@Test fun `route links parse direction sequence and endpoints`() = runTest {
    enqueueSuccess("""[
      {"linkId":"l2","stNode":"n2","edNode":"n3","gisDist":100,"moveDir":"0","linkSeq":2},
      {"linkId":"l1","stNode":"n1","edNode":"n2","gisDist":80,"moveDir":"0","linkSeq":1}
    ]""")

    val result = remote.routeLinks("key", "route") as RemoteResult.Success

    assertEquals(listOf("l1", "l2"), result.value.map(RouteLink::linkId))
    assertEquals("route", server.takeRequest().requestUrl!!.queryParameter("routeId"))
}

@Test fun `nonempty malformed node and link arrays fail the contract`() = runTest {
    enqueueSuccess("""{"route":[],"bs":[],"node":[{"nodeId":"n1"}],"link":[]}""")
    assertEquals(RemoteResult.Failure(BusDataError.MalformedResponse), remote.basicNodes("key"))

    enqueueSuccess("""[{"linkId":"l1","moveDir":"0"}]""")
    assertEquals(RemoteResult.Failure(BusDataError.MalformedResponse), remote.routeLinks("key", "route"))
}
```

- [ ] **Step 2: Run the focused remote tests and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSourceTest*node*" --tests "com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSourceTest*link*"
```

Expected: compilation FAIL because the new remote methods are absent.

- [ ] **Step 3: Extend the remote interface and OkHttp implementation**

Add to `DaeguBusRemoteDataSource`:

```kotlin
suspend fun basicNodes(serviceKey: String): RemoteResult<List<RouteNode>> = RemoteResult.Success(emptyList())
suspend fun routeLinks(serviceKey: String, routeId: String): RemoteResult<List<RouteLink>> = RemoteResult.Success(emptyList())
```

Add imports and these methods to `OkHttpDaeguBusRemoteDataSource`:

```kotlin
override suspend fun basicNodes(serviceKey: String): RemoteResult<List<RouteNode>> =
    when (val result = request("getBasic02", serviceKey, emptyMap())) {
        is RemoteResult.Success -> parseBasicNodes(result.value)
        is RemoteResult.Failure -> result
    }

override suspend fun routeLinks(serviceKey: String, routeId: String): RemoteResult<List<RouteLink>> =
    when (val result = request("getLink02", serviceKey, mapOf("routeId" to routeId))) {
        is RemoteResult.Success -> parseRouteLinks(routeId, result.value)
        is RemoteResult.Failure -> result
    }

private fun parseBasicNodes(items: JsonElement): RemoteResult<List<RouteNode>> {
    val array = items.takeIf(JsonElement::isJsonObject)?.asJsonObject
        ?.get("node")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        ?: return RemoteResult.Failure(BusDataError.MalformedResponse)
    val nodes = array.mapNotNull { element ->
        val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
        RouteNode(
            nodeId = item.string("nodeId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            longitude = item.double("xPos") ?: return@mapNotNull null,
            latitude = item.double("yPos") ?: return@mapNotNull null,
        )
    }
    return if (array.size() > 0 && nodes.isEmpty()) RemoteResult.Failure(BusDataError.MalformedResponse)
    else RemoteResult.Success(nodes)
}

private fun parseRouteLinks(routeId: String, items: JsonElement): RemoteResult<List<RouteLink>> {
    if (!items.isJsonArray) return RemoteResult.Failure(BusDataError.MalformedResponse)
    val array = items.asJsonArray
    val links = array.mapNotNull { element ->
        val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
        RouteLink(
            routeId = routeId,
            linkId = item.string("linkId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            moveDirection = item.string("moveDir")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            sequence = item.int("linkSeq") ?: return@mapNotNull null,
            startNodeId = item.string("stNode")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            endNodeId = item.string("edNode")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
        )
    }
    return if (array.size() > 0 && links.isEmpty()) RemoteResult.Failure(BusDataError.MalformedResponse)
    else RemoteResult.Success(links.sortedWith(compareBy(RouteLink::moveDirection, RouteLink::sequence)))
}
```

Use the existing `enqueueSuccess(itemsJson)` test helper; do not add raw-response logging.

- [ ] **Step 4: Run all remote parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSourceTest
```

Expected: all existing and new remote tests PASS.

- [ ] **Step 5: Commit the API extension**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/data/remote app/src/test/kotlin/com/rafaam11/businfo/data/remote
git commit -m "feat: parse route nodes and links"
```

---

### Task 4: Persist direction-specific route geometry with a Room migration

**Files:**
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusDatabase.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusDatabaseMigrations.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusLocalDataSource.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/local/RoomBusLocalDataSource.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Modify: `app/src/androidTest/kotlin/com/rafaam11/businfo/data/local/RoomBusLocalDataSourceTest.kt`
- Create: `app/src/androidTest/kotlin/com/rafaam11/businfo/data/local/BusDatabaseMigrationTest.kt`
- Generate: `app/schemas/com.rafaam11.businfo.data.local.BusDatabase/2.json`

**Interfaces:**
- Consumes: `RouteGeometry` from Task 2.
- Produces: `routeGeometry(routeId, moveDirection)` and `saveRouteGeometry(geometry)`; preserves all version-1 tables and user data.

- [ ] **Step 1: Add an instrumentation round-trip test**

Add to `RoomBusLocalDataSourceTest`:

```kotlin
@Test fun routeGeometryRoundTripsAsSeparateSegments() = runTest {
    val geometry = RouteGeometry(
        "route", "0",
        listOf(
            RouteSegment(listOf("a", "b"), listOf(GeoPoint(128.60, 35.80), GeoPoint(128.61, 35.81))),
            RouteSegment(listOf("x", "y"), listOf(GeoPoint(128.70, 35.90), GeoPoint(128.71, 35.91))),
        ),
        Instant.parse("2026-07-17T12:00:00Z"),
    )

    local.saveRouteGeometry(geometry)

    assertEquals(geometry, local.routeGeometry("route", "0"))
    assertEquals(null, local.routeGeometry("route", "1"))
}
```

Create `BusDatabaseMigrationTest.kt` to prove the new table does not replace a version-1 favorite:

```kotlin
class BusDatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BusDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
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
}
```

- [ ] **Step 2: Confirm the Android test source does not compile yet**

Run:

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

Expected: compilation FAIL because geometry persistence methods do not exist. Do not run the connected test.

- [ ] **Step 3: Add the entity, DAO methods, data-source mapping, and migration**

Add to `BusDatabase.kt`:

```kotlin
@Entity(tableName = "route_geometries", primaryKeys = ["routeId", "moveDirection"])
data class RouteGeometryEntity(
    val routeId: String,
    val moveDirection: String,
    val segmentsJson: String,
    val fetchedAtEpochMillis: Long,
)

@Query("SELECT * FROM route_geometries WHERE routeId = :routeId AND moveDirection = :moveDirection")
abstract suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometryEntity?

@Insert(onConflict = OnConflictStrategy.REPLACE)
abstract suspend fun saveRouteGeometry(geometry: RouteGeometryEntity)
```

Add `RouteGeometryEntity::class` to `entities` and change the database version to `2`.

Create `BusDatabaseMigrations.kt`:

```kotlin
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
```

Add to `BusLocalDataSource`:

```kotlin
suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometry?
suspend fun saveRouteGeometry(geometry: RouteGeometry)
```

Implement in `RoomBusLocalDataSource`:

```kotlin
override suspend fun routeGeometry(routeId: String, moveDirection: String): RouteGeometry? =
    dao.routeGeometry(routeId, moveDirection)?.let { entity ->
        RouteGeometry(
            entity.routeId,
            entity.moveDirection,
            gson.fromJson(entity.segmentsJson, ROUTE_SEGMENTS_TYPE),
            Instant.ofEpochMilli(entity.fetchedAtEpochMillis),
        )
    }

override suspend fun saveRouteGeometry(geometry: RouteGeometry) = dao.saveRouteGeometry(
    RouteGeometryEntity(
        geometry.routeId,
        geometry.moveDirection,
        gson.toJson(geometry.segments),
        geometry.fetchedAt.toEpochMilli(),
    ),
)

private companion object {
    val ROUTE_SEGMENTS_TYPE = object : TypeToken<List<RouteSegment>>() {}.type
}
```

Update `AppGraph` database construction:

```kotlin
private val database = Room.databaseBuilder(
    context.applicationContext,
    BusDatabase::class.java,
    "bus-info.db",
).addMigrations(MIGRATION_1_2).build()
```

- [ ] **Step 4: Generate schema 2 and compile the Android test**

Run:

```powershell
.\gradlew.bat :app:kspDebugKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

Expected: BUILD SUCCESSFUL and `app/schemas/com.rafaam11.businfo.data.local.BusDatabase/2.json` exists. Do not run connected instrumentation yet.

- [ ] **Step 5: Commit the non-destructive database upgrade**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/data/local app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt app/src/androidTest/kotlin/com/rafaam11/businfo/data/local app/schemas
git commit -m "feat: cache route geometry in Room"
```

---

### Task 5: Add route-geometry and direction-filtered vehicle repositories

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/RouteGeometryRepository.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/VehiclePositionRepository.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/data/RouteGeometryRepositoryTest.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/data/VehiclePositionRepositoryTest.kt`
- Modify: test fakes implementing `BusLocalDataSource` as required by the new geometry methods.

**Interfaces:**
- Consumes: `CredentialStore`, remote node/link/vehicle methods, `BusLocalDataSource`, `Clock`, and `FavoriteSelection`.
- Produces: `RouteMapLoadResult`, `VehiclePositionDataSource.refresh(selection)`, and successful empty batches that replace prior visible vehicles.

- [ ] **Step 1: Write repository tests for cache, fallback, direction, and empty success**

The focused assertions must cover these exact cases:

```kotlin
@Test fun `fresh geometry cache avoids node and link requests`() = runTest {
    local.geometry = geometry(fetchedAt = now.minusSeconds(86_399))
    val result = geometryRepository.load(selection)
    assertEquals(RouteMapLoadResult.Success(local.geometry!!, stops, null), result)
    assertEquals(0, remote.basicNodeCalls)
    assertEquals(0, remote.routeLinkCalls)
}

@Test fun `failed geometry refresh retains cache and reports warning`() = runTest {
    local.geometry = geometry(fetchedAt = now.minusSeconds(86_401))
    remote.nodeResult = RemoteResult.Failure(BusDataError.NetworkUnavailable)
    val result = geometryRepository.load(selection) as RouteMapLoadResult.Success
    assertEquals(local.geometry, result.geometry)
    assertEquals(BusDataError.NetworkUnavailable, result.warning)
}

@Test fun `vehicle refresh exposes and stores only a current empty state`() = runTest {
    local.batch = VehicleBatch.from(listOf(vehicle(direction = "0")), now.minusSeconds(60))
    remote.vehicleResult = RemoteResult.Success(emptyList())
    val result = vehicleRepository.refresh(selection) as VehicleLoadResult.Success
    assertTrue(result.batch.vehicles.isEmpty())
    assertEquals(now, result.batch.fetchedAt)
    assertTrue(local.batch!!.vehicles.isEmpty())
}

@Test fun `vehicle refresh returns only selected direction`() = runTest {
    remote.vehicleResult = RemoteResult.Success(listOf(vehicle("0"), vehicle("1")))
    val result = vehicleRepository.refresh(selection) as VehicleLoadResult.Success
    assertEquals(listOf("0"), result.batch.vehicles.map(VehicleSnapshot::moveDirection))
}
```

- [ ] **Step 2: Run the new repository tests and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.data.RouteGeometryRepositoryTest --tests com.rafaam11.businfo.data.VehiclePositionRepositoryTest
```

Expected: compilation FAIL because the repositories and result types do not exist.

- [ ] **Step 3: Implement route geometry loading and 24-hour fallback**

Create `RouteGeometryRepository.kt` with these public contracts and load flow:

```kotlin
sealed interface RouteMapLoadResult {
    data class Success(
        val geometry: RouteGeometry,
        val stops: List<RouteStop>,
        val warning: BusDataError?,
    ) : RouteMapLoadResult
    data class Failure(val error: BusDataError) : RouteMapLoadResult
}

interface RouteGeometryDataSource {
    suspend fun load(selection: FavoriteSelection, force: Boolean = false): RouteMapLoadResult
}

class RouteGeometryRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val local: BusLocalDataSource,
    private val clock: Clock,
) : RouteGeometryDataSource {
    override suspend fun load(selection: FavoriteSelection, force: Boolean): RouteMapLoadResult {
        val cached = local.routeGeometry(selection.routeId, selection.directionCode)
        var stops = local.routeStops(selection.routeId).filter { it.moveDirection == selection.directionCode }
        val fresh = cached != null && Duration.between(cached.fetchedAt, clock.instant()) < Duration.ofHours(24)
        if (!force && fresh && stops.isNotEmpty()) return RouteMapLoadResult.Success(requireNotNull(cached), stops, null)
        val key = credentials.read() ?: return cached.asFallback(stops, BusDataError.InvalidCredential)

        if (stops.isEmpty()) {
            when (val stopResult = remote.routeStops(key, selection.routeId)) {
                is RemoteResult.Failure -> return cached.asFallback(stops, stopResult.error)
                is RemoteResult.Success -> {
                    if (stopResult.value.isNotEmpty()) local.replaceRouteStops(selection.routeId, stopResult.value)
                    stops = stopResult.value.filter { it.moveDirection == selection.directionCode }
                }
            }
        }
        if (!force && fresh) return RouteMapLoadResult.Success(requireNotNull(cached), stops, null)

        val nodes = remote.basicNodes(key)
        if (nodes is RemoteResult.Failure) return cached.asFallback(stops, nodes.error)
        val links = remote.routeLinks(key, selection.routeId)
        if (links is RemoteResult.Failure) return cached.asFallback(stops, links.error)
        val geometry = RouteGeometryAssembler.assemble(
            selection.routeId,
            selection.directionCode,
            (nodes as RemoteResult.Success).value,
            (links as RemoteResult.Success).value,
            clock.instant(),
        )
        if (geometry.segments.isEmpty()) return cached.asFallback(stops, BusDataError.MalformedResponse)
        local.saveRouteGeometry(geometry)
        return RouteMapLoadResult.Success(geometry, stops, null)
    }

    private fun RouteGeometry?.asFallback(stops: List<RouteStop>, error: BusDataError): RouteMapLoadResult =
        this?.let { RouteMapLoadResult.Success(it, stops, error) } ?: RouteMapLoadResult.Failure(error)
}
```

- [ ] **Step 4: Implement successful-empty replacement and direction filtering**

Create `VehiclePositionRepository.kt`:

```kotlin
interface VehiclePositionDataSource {
    suspend fun refresh(selection: FavoriteSelection): VehicleLoadResult
}

class VehiclePositionRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val local: BusLocalDataSource,
    private val clock: Clock,
) : VehiclePositionDataSource {
    private val requestMutex = Mutex()

    override suspend fun refresh(selection: FavoriteSelection): VehicleLoadResult = requestMutex.withLock {
        val retained = local.vehicleBatch(selection.routeId)?.forDirection(selection.directionCode)
        val key = credentials.read() ?: return@withLock VehicleLoadResult.Failure(BusDataError.InvalidCredential, retained)
        when (val result = remote.vehicles(key, selection.routeId)) {
            is RemoteResult.Failure -> VehicleLoadResult.Failure(result.error, retained)
            is RemoteResult.Success -> {
                val complete = VehicleBatch.from(result.value, clock.instant())
                local.saveVehicleBatch(selection.routeId, complete)
                VehicleLoadResult.Success(complete.forDirection(selection.directionCode))
            }
        }
    }

    private fun VehicleBatch.forDirection(direction: String) =
        VehicleBatch.from(vehicles.filter { it.moveDirection == direction }, fetchedAt)
}
```

The mutex is the single request gate: polling and retry cannot execute overlapping `getPos02` calls.

- [ ] **Step 5: Run repository tests and all existing unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all unit tests PASS. Update every existing fake `BusLocalDataSource` with in-memory geometry methods; do not return fabricated production data.

- [ ] **Step 6: Commit both repository boundaries**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/data app/src/test/kotlin/com/rafaam11/businfo/data app/src/test/kotlin/com/rafaam11/businfo/ui
git commit -m "feat: load realtime map data"
```

---

### Task 6: Drive polling, lifecycle, and 30-second hiding in a dedicated ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapUiState.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModelTest.kt`

**Interfaces:**
- Consumes: `DashboardDataSource.favorite(slot)`, `RouteGeometryDataSource`, `VehiclePositionDataSource`, `PollingPolicy`, `FreshnessPolicy`, `Clock`.
- Produces: `StateFlow<RealtimeMapUiState>`, `open(slot)`, `setVisible(Boolean)`, `retry()`, `selectVehicle(key)`, `close()`.

- [ ] **Step 1: Write deterministic coroutine tests with a fake clock**

Cover these behaviors with `StandardTestDispatcher` and a mutable `Clock`:

```kotlin
@Test fun `opening visible map loads geometry and polls immediately then after eight seconds`() = runTest {
    viewModel.setVisible(true)
    viewModel.open(CommuteSlot.MORNING)
    runCurrent()
    assertEquals(1, vehicles.calls)

    advanceTimeBy(7_999)
    assertEquals(1, vehicles.calls)
    advanceTimeBy(1)
    runCurrent()
    assertEquals(2, vehicles.calls)
}

@Test fun `background visibility cancels polling`() = runTest {
    viewModel.setVisible(true)
    viewModel.open(CommuteSlot.MORNING)
    runCurrent()
    viewModel.setVisible(false)
    advanceTimeBy(60_000)
    assertEquals(1, vehicles.calls)
}

@Test fun `freshness ticker hides vehicles after thirty seconds`() = runTest {
    viewModel.setVisible(true)
    viewModel.open(CommuteSlot.MORNING)
    runCurrent()
    clock.advanceSeconds(31)
    advanceTimeBy(1_000)
    runCurrent()
    assertTrue(viewModel.uiState.value.visibleVehicles.isEmpty())
    assertEquals(DataFreshness.STALE, viewModel.uiState.value.freshness)
}

@Test fun `authentication failure stops future polls`() = runTest {
    vehicles.results += VehicleLoadResult.Failure(BusDataError.InvalidCredential, null)
    viewModel.setVisible(true)
    viewModel.open(CommuteSlot.MORNING)
    runCurrent()
    advanceTimeBy(120_000)
    assertEquals(1, vehicles.calls)
}
```

- [ ] **Step 2: Run the ViewModel test and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.ui.RealtimeMapViewModelTest
```

Expected: compilation FAIL because the map state and ViewModel do not exist.

- [ ] **Step 3: Define immutable UI models and ephemeral vehicle keys**

Create `RealtimeMapUiState.kt`:

```kotlin
data class MapVehicleUi(
    val key: String,
    val point: GeoPoint,
    val stopName: String,
    val stopSequence: Int?,
    val remainingStops: Int?,
    val arrivalState: String?,
)

data class RealtimeMapUiState(
    val selection: FavoriteSelection? = null,
    val geometry: RouteGeometry? = null,
    val stops: List<RouteStop> = emptyList(),
    val vehicleBatch: VehicleBatch? = null,
    val visibleVehicles: List<MapVehicleUi> = emptyList(),
    val freshness: DataFreshness = DataFreshness.UNAVAILABLE,
    val loadingGeometry: Boolean = false,
    val geometryError: BusDataError? = null,
    val vehicleError: BusDataError? = null,
    val mapErrorCode: String? = null,
    val selectedVehicleKey: String? = null,
)
```

Create a mapper that sorts by sequence and derives a snapshot-only key without `vhcNo2`:

```kotlin
fun mapVehicles(selection: FavoriteSelection, stops: List<RouteStop>, batch: VehicleBatch): List<MapVehicleUi> {
    val stopById = stops.associateBy(RouteStop::stopId)
    val targetSequence = stops.firstOrNull { it.stopId == selection.stopId }?.sequence
    return batch.vehicles.sortedBy { it.stopSequence ?: Int.MAX_VALUE }.mapIndexed { index, vehicle ->
        val stop = vehicle.stopId?.let(stopById::get)
        MapVehicleUi(
            key = listOf(batch.fetchedAt.toEpochMilli(), index, vehicle.stopSequence, vehicle.latitude, vehicle.longitude).joinToString(":"),
            point = GeoPoint(vehicle.longitude, vehicle.latitude),
            stopName = stop?.stopName ?: "정류장 정보 없음",
            stopSequence = vehicle.stopSequence,
            remainingStops = vehicle.stopSequence?.let { seq -> targetSequence?.minus(seq) },
            arrivalState = vehicle.arrivalState,
        )
    }
}
```

- [ ] **Step 4: Implement the single polling job and one-second freshness ticker**

Implement `RealtimeMapViewModel` so `open(slot)` loads the favorite and geometry once, `setVisible(true)` starts at most one poll and ticker, and `setVisible(false)` cancels both. Bootstrap with this state transition:

```kotlin
fun open(slot: CommuteSlot) {
    if (openedSlot == slot && _uiState.value.selection != null) {
        startJobsIfReady()
        return
    }
    bootstrapJob?.cancel()
    pollingJob?.cancel()
    freshnessJob?.cancel()
    openedSlot = slot
    _uiState.value = RealtimeMapUiState(loadingGeometry = true)
    bootstrapJob = viewModelScope.launch(dispatcher) {
        val selection = dashboard.favorite(slot)
        if (selection == null) {
            _uiState.value = RealtimeMapUiState(geometryError = BusDataError.MalformedResponse)
            return@launch
        }
        _uiState.value = _uiState.value.copy(selection = selection)
        when (val result = geometry.load(selection)) {
            is RouteMapLoadResult.Failure -> {
                _uiState.value = _uiState.value.copy(loadingGeometry = false, geometryError = result.error)
            }
            is RouteMapLoadResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    geometry = result.geometry,
                    stops = result.stops,
                    loadingGeometry = false,
                    geometryError = result.warning,
                )
                startJobsIfReady()
            }
        }
    }
}
```

The core polling loops must be:

```kotlin
private fun startJobsIfReady() {
    val selection = _uiState.value.selection ?: return
    if (!visible || pollingJob?.isActive == true) return
    pollingJob = viewModelScope.launch(dispatcher) {
        var consecutiveFailures = 0
        while (isActive && visible) {
            when (val result = vehicles.refresh(selection)) {
                is VehicleLoadResult.Success -> {
                    consecutiveFailures = 0
                    publishBatch(result.batch, null)
                    delay(8.seconds)
                }
                is VehicleLoadResult.Failure -> {
                    publishBatch(result.retained, result.error)
                    val pollResult = when (result.error) {
                        BusDataError.InvalidCredential -> PollResult.AuthenticationFailure
                        BusDataError.RateLimited -> PollResult.QuotaExceeded
                        else -> PollResult.TransientFailure(++consecutiveFailures)
                    }
                    when (val decision = PollingPolicy.after(pollResult)) {
                        PollDecision.Stop -> return@launch
                        is PollDecision.Wait -> delay(decision.seconds.seconds)
                    }
                }
            }
        }
    }
    freshnessJob = viewModelScope.launch(dispatcher) {
        while (isActive && visible) {
            publishFreshness()
            delay(1.seconds)
        }
    }
}

private fun publishFreshness() {
    val current = _uiState.value
    val freshness = FreshnessPolicy.classify(current.vehicleBatch?.fetchedAt, clock.instant())
    val visible = if (freshness == DataFreshness.STALE) emptyList()
        else current.vehicleBatch?.let { mapVehicles(requireNotNull(current.selection), current.stops, it) }.orEmpty()
    _uiState.value = current.copy(freshness = freshness, visibleVehicles = visible)
}
```

On each successful batch, clear `selectedVehicleKey`; an empty success therefore clears markers immediately. On `close()`, cancel jobs and reset state. If geometry is absent, `retry()` calls `geometry.load(selection, force = true)` and only starts polling after that succeeds. Otherwise `retry()` cancels a pending backoff job and starts one immediate replacement polling job; the repository mutex still prevents overlapping HTTP calls.

- [ ] **Step 5: Run ViewModel and policy tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.ui.RealtimeMapViewModelTest --tests com.rafaam11.businfo.domain.PollingPolicyTest --tests com.rafaam11.businfo.domain.FreshnessPolicyTest
```

Expected: all focused tests PASS with virtual time; no real delays occur.

- [ ] **Step 6: Commit the lifecycle-aware state holder**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapUiState.kt app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt app/src/test/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModelTest.kt
git commit -m "feat: poll realtime map while visible"
```

---

### Task 7: Build the testable map-and-bottom-sheet Compose shell

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapScreen.kt`
- Create: `app/src/androidTest/kotlin/com/rafaam11/businfo/ui/RealtimeMapScreenTest.kt`

**Interfaces:**
- Consumes: `RealtimeMapUiState`, callbacks, and injectable `mapContent`.
- Produces: approved map-first layout with a partially expanded bottom sheet and stable test tags.

- [ ] **Step 1: Write UI tests with a fake map surface**

Create `RealtimeMapScreenTest.kt` with these fixtures and pass a fake map surface:

```kotlin
private val selection = FavoriteSelection(
    CommuteSlot.MORNING, "route", "급행8-1", "0", "검단동 방면", "stop", "효동초등학교건너",
)
private val vehicle = VehicleSnapshot("route", "급행8-1", "0", "nearby", 5, 35.81, 128.61, null, null, null)
private val batch = VehicleBatch.from(listOf(vehicle), Instant.parse("2026-07-17T12:00:00Z"))
private val normalState = RealtimeMapUiState(
    selection = selection,
    vehicleBatch = batch,
    visibleVehicles = listOf(MapVehicleUi("snapshot:0", GeoPoint(128.61, 35.81), "동대구역", 5, 2, null)),
    freshness = DataFreshness.FRESH,
)
private val staleState = normalState.copy(visibleVehicles = emptyList(), freshness = DataFreshness.STALE)
private val emptyState = normalState.copy(
    vehicleBatch = VehicleBatch.from(emptyList(), Instant.parse("2026-07-17T12:00:00Z")),
    visibleVehicles = emptyList(),
)

@Test fun normalStateShowsRouteSummaryAndSelectedDirectionVehicles() {
    compose.setContent {
        MaterialTheme {
            RealtimeMapScreen(
                state = normalState,
                onBack = {}, onRetry = {}, onVehicleSelected = {}, onFitRoute = {},
                mapContent = { _, _ -> Text("가짜 지도") },
            )
        }
    }
    compose.onNodeWithText("가짜 지도").assertIsDisplayed()
    compose.onNodeWithText("급행8-1 · 검단동 방면").assertIsDisplayed()
    compose.onNodeWithText("내 정류장 · 효동초등학교건너").assertIsDisplayed()
}

@Test fun staleStateExplainsWhyMarkersAreHidden() {
    compose.setContent {
        MaterialTheme {
            RealtimeMapScreen(staleState, {}, {}, {}, {}, mapContent = { _, _ -> })
        }
    }
    compose.onNodeWithText("위치 정보가 지연되고 있습니다").assertIsDisplayed()
}

@Test fun successfulEmptyStateShowsNoOperatingVehicles() {
    compose.setContent {
        MaterialTheme {
            RealtimeMapScreen(emptyState, {}, {}, {}, {}, mapContent = { _, _ -> })
        }
    }
    compose.onNodeWithText("현재 운행 차량 없음").assertIsDisplayed()
}
```

- [ ] **Step 2: Confirm the Android test source fails to compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

Expected: compilation FAIL because `RealtimeMapScreen` does not exist. Do not run a connected test.

- [ ] **Step 3: Implement the Material 3 screen shell**

Create `RealtimeMapScreen.kt` with this public boundary:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeMapScreen(
    state: RealtimeMapUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVehicleSelected: (String) -> Unit,
    onFitRoute: () -> Unit,
    mapContent: @Composable (
        RealtimeMapUiState,
        (String) -> Unit,
    ) -> Unit,
) {
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    BottomSheetScaffold(
        sheetPeekHeight = 132.dp,
        sheetContent = {
            RealtimeMapSheet(
                state = state,
                onRetry = onRetry,
                onVehicleSelected = onVehicleSelected,
            )
        },
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        scaffoldState = rememberBottomSheetScaffoldState(sheetState),
        topBar = {
            TopAppBar(
                title = { Text(state.selection?.let { "${it.routeNo} 실시간" } ?: "실시간 버스") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
                actions = { TextButton(onClick = onFitRoute) { Text("노선 전체 보기") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.mapErrorCode != null) {
                MapBlockingError("네이버 지도를 인증할 수 없습니다. 코드 ${state.mapErrorCode}", onRetry)
            } else if (state.geometry == null && state.geometryError != null) {
                MapBlockingError(state.geometryError.userMessage(), onRetry)
            } else {
                mapContent(state, onVehicleSelected)
                if (state.loadingGeometry) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
```

`RealtimeMapSheet` must render these exact state messages:

```kotlin
@Composable
private fun RealtimeMapSheet(
    state: RealtimeMapUiState,
    onRetry: () -> Unit,
    onVehicleSelected: (String) -> Unit,
) {
    val selection = state.selection
    val statusText = when {
        state.vehicleBatch?.vehicles?.isEmpty() == true && state.vehicleError == null -> "현재 운행 차량 없음"
        state.freshness == DataFreshness.STALE -> "위치 정보가 지연되고 있습니다"
        state.vehicleError != null -> state.vehicleError.userMessage()
        else -> "운행 차량 ${state.visibleVehicles.size}대"
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            selection?.let { "${it.routeNo} · ${it.directionLabel}" } ?: "노선 준비 중",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        selection?.let { Text("내 정류장 · ${it.stopName}") }
        Text(statusText)
        if (state.geometry != null && state.geometryError != null) {
            Text("캐시된 노선선을 표시하고 있습니다", color = MaterialTheme.colorScheme.error)
        }
        state.visibleVehicles.forEach { vehicle ->
            val remaining = when {
                vehicle.remainingStops == null -> "남은 정류장 확인 불가"
                vehicle.remainingStops > 0 -> "${vehicle.remainingStops}정거장 전"
                vehicle.remainingStops == 0 -> "내 정류장 도착"
                else -> "내 정류장 통과"
            }
            Card(
                onClick = { onVehicleSelected(vehicle.key) },
                colors = CardDefaults.cardColors(
                    containerColor = if (vehicle.key == state.selectedVehicleKey) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(vehicle.stopName, fontWeight = FontWeight.SemiBold)
                    Text(remaining)
                }
            }
        }
        if (state.vehicleError != null || state.geometryError != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("다시 시도") }
        }
    }
}

@Composable
private fun MapBlockingError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}
```

If `geometry` is present while `geometryError` is non-null, show the error as a non-blocking `캐시된 노선선을 표시하고 있습니다` banner inside the sheet. If `selectedVehicleKey` matches a row, apply the selected container color; do not carry that selection into a later batch.

- [ ] **Step 4: Compile the UI tests and assemble the screen**

Run:

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Do not execute instrumentation on the installed personal app yet.

- [ ] **Step 5: Commit the Compose shell**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapScreen.kt app/src/androidTest/kotlin/com/rafaam11/businfo/ui/RealtimeMapScreenTest.kt
git commit -m "feat: add realtime map bottom sheet"
```

---

### Task 8: Render NAVER route, stop, and vehicle overlays

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/MapAuthMonitor.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/NaverRealtimeMap.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayController.kt`
- Create: `app/src/main/res/drawable/ic_bus_marker.xml`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/ui/map/MapAuthMonitorTest.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt`

**Interfaces:**
- Consumes: `RealtimeMapUiState`, NAVER `MapView`, `PathOverlay`, `Marker`, and auth callbacks.
- Produces: `NaverRealtimeMap(...)`, `MapAuthMonitor.errorCode`, one-time camera fitting, and overlay cleanup.

- [ ] **Step 1: Write a JVM test for map authentication reporting**

```kotlin
@Test fun `map auth monitor exposes and clears the SDK code`() {
    val monitor = MapAuthMonitor()
    monitor.report("401")
    assertEquals("401", monitor.errorCode.value)
    monitor.clear()
    assertEquals(null, monitor.errorCode.value)
}
```

- [ ] **Step 2: Run the auth monitor test and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.ui.map.MapAuthMonitorTest
```

Expected: compilation FAIL because `MapAuthMonitor` does not exist.

- [ ] **Step 3: Implement auth reporting and wire the official SDK callback**

Create `MapAuthMonitor.kt`:

```kotlin
class MapAuthMonitor {
    private val _errorCode = MutableStateFlow<String?>(null)
    val errorCode: StateFlow<String?> = _errorCode.asStateFlow()
    fun report(code: String) { _errorCode.value = code }
    fun clear() { _errorCode.value = null }
}
```

Expose `val mapAuthMonitor = MapAuthMonitor()` from `AppGraph`. In `MainActivity.onCreate`, register:

```kotlin
NaverMapSdk.getInstance(applicationContext).setOnAuthFailedListener { exception ->
    graph.mapAuthMonitor.report(exception.errorCode)
}
```

Pass the monitor to `RealtimeMapViewModel`; collect it with this dedicated job so a map error is distinct and the Daegu credential remains unchanged:

```kotlin
private val mapAuthJob = viewModelScope.launch(dispatcher) {
    mapAuthMonitor.errorCode.filterNotNull().collect { code ->
        pollingJob?.cancel()
        freshnessJob?.cancel()
        _uiState.value = _uiState.value.copy(mapErrorCode = code, visibleVehicles = emptyList())
    }
}
```

`retry()` clears `MapAuthMonitor` only to allow a fresh SDK attempt; it must not clear or rewrite the Daegu service key.

- [ ] **Step 4: Implement `MapView` lifecycle and overlay controller**

`NaverRealtimeMap` must:

```kotlin
@Composable
fun NaverRealtimeMap(
    state: RealtimeMapUiState,
    onVehicleSelected: (String) -> Unit,
    fitRouteRequest: Int,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val savedState = rememberSaveable { Bundle() }
    val mapView = remember { MapView(context).also { it.onCreate(savedState) } }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val controller = remember { NaverMapOverlayController() }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { if (!resumed) { start(); mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { if (started) { pause(); mapView.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onLowMemory() = mapView.onLowMemory()
            override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory() }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }
        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(callbacks)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        mapView.getMapAsync { naverMap = it }
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            controller.clear()
            stop()
            mapView.onSaveInstanceState(savedState)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    LaunchedEffect(naverMap, state.geometry, state.stops, state.visibleVehicles) {
        naverMap?.let { controller.render(it, state, onVehicleSelected) }
    }
    LaunchedEffect(naverMap, state.geometry, fitRouteRequest) {
        naverMap?.let { controller.fitRouteOnceOrOnRequest(it, state, fitRouteRequest) }
    }
}
```

This lifecycle bridge handles both future lifecycle events and a destination first composed while its owner is already STARTED or RESUMED. Keep the start/resume flags so disposal remains symmetric before `onDestroy`.

Create the vehicle icon as a vector drawable:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="36dp" android:height="36dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#005BAC"
        android:pathData="M4,16c0,0.88 0.39,1.67 1,2.22V22h2v-2h10v2h2v-2.12c0.61,-0.55 1,-1.34 1,-2.22V6c0,-3.5 -3.58,-4 -8,-4s-8,0.5 -8,4v10zM7.5,18C6.67,18 6,17.33 6,16.5S6.67,15 7.5,15 9,15.67 9,16.5 8.33,18 7.5,18zM16.5,18c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5zM18,11H6V6h12v5z" />
</vector>
```

`NaverMapOverlayController.render` must maintain separate collections for path, stop, and vehicle overlays. Use one `PathOverlay` per `RouteSegment` with at least 2 points. Use a distinct marker tint for the favorite stop. Rebuild vehicle markers on every confirmed snapshot with `OverlayImage.fromResource(R.drawable.ic_bus_marker)`, set each marker's tag to the ephemeral `MapVehicleUi.key`, and call `onVehicleSelected(key)` from the overlay click listener. Never animate marker positions.

Use this camera rule:

```kotlin
private fun RouteGeometry?.orEmptyPoints(): List<GeoPoint> =
    this?.segments.orEmpty().flatMap(RouteSegment::points)

val points = state.geometry.orEmptyPoints() + state.visibleVehicles.map(MapVehicleUi::point)
val initialDataReady = state.vehicleBatch != null || state.vehicleError != null
if (points.isNotEmpty() && ((initialFitPending && initialDataReady) || fitRouteRequest != lastFitRequest)) {
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
    map.moveCamera(CameraUpdate.fitBounds(builder.build(), 64))
    initialFitPending = false
    lastFitRequest = fitRouteRequest
}
```

Do not move the camera on ordinary 8-second vehicle updates after the initial fit.

- [ ] **Step 5: Run the pure auth test and compile the NAVER adapter**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.ui.map.MapAuthMonitorTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. A blank local NCP key may prevent runtime map authentication but must not prevent compilation.

- [ ] **Step 6: Commit the SDK adapter**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/ui/map app/src/test/kotlin/com/rafaam11/businfo/ui/map app/src/main/res/drawable/ic_bus_marker.xml app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt
git commit -m "feat: render realtime vehicles on Naver map"
```

---

### Task 9: Integrate navigation, remove the legacy detail state, and verify the APK

**Files:**
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/BusAppViewModel.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/DashboardUiState.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/DashboardScreens.kt`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/ui/BusAppViewModelTest.kt`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `README.md`

**Interfaces:**
- Consumes: all prior task outputs.
- Produces: dashboard card -> `map/{slot}` navigation, lifecycle-aware polling, version `0.3.0`/code `3`, and a debug APK ready for user-approved device verification.

- [ ] **Step 1: Add a navigation/presentation regression test**

Add this source contract test to `FoundationContractTest`; it proves the old destination and state owner are removed without attempting to reference deleted Kotlin members:

```kotlin
@Test fun dashboardNavigationTargetsRealtimeMap() {
    val repoRoot = File(requireNotNull(System.getProperty("user.dir"))).let { cwd ->
        if (File(cwd, "gradle/libs.versions.toml").isFile) cwd else requireNotNull(cwd.parentFile)
    }
    val app = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt").readText()
    val dashboardViewModel = File(repoRoot, "app/src/main/kotlin/com/rafaam11/businfo/ui/BusAppViewModel.kt").readText()
    assertTrue(app.contains("map/{slot}"))
    assertFalse(app.contains("detail/{slot}"))
    assertTrue(app.contains("realtimeMapViewModel.setVisible"))
    assertFalse(dashboardViewModel.contains("detailState"))
    assertFalse(dashboardViewModel.contains("loadDetail"))
}
```

- [ ] **Step 2: Run the regression test and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.FoundationContractTest.dashboardNavigationTargetsRealtimeMap
```

Expected: FAIL because navigation still targets `detail/{slot}`.

- [ ] **Step 3: Wire the dedicated ViewModel and destination lifecycle**

Expose repositories from `AppGraph`:

```kotlin
val routeGeometryRepository = RouteGeometryRepository(credentials, remote, local, Clock.systemUTC())
val vehiclePositionRepository = VehiclePositionRepository(credentials, remote, local, Clock.systemUTC())
```

Extend the `ViewModelProvider.Factory` in `MainActivity` to create both ViewModels and obtain them from the same activity store:

```kotlin
val factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(BusAppViewModel::class.java) ->
            BusAppViewModel(graph.credentialRepository, graph.dashboardRepository) as T
        modelClass.isAssignableFrom(RealtimeMapViewModel::class.java) ->
            RealtimeMapViewModel(
                graph.dashboardRepository,
                graph.routeGeometryRepository,
                graph.vehiclePositionRepository,
                graph.mapAuthMonitor,
                Clock.systemUTC(),
            ) as T
        else -> error("Unsupported ViewModel ${modelClass.name}")
    }
}
val provider = ViewModelProvider(this, factory)
val busViewModel = provider[BusAppViewModel::class.java]
val realtimeMapViewModel = provider[RealtimeMapViewModel::class.java]
setContent { BusInfoApp(busViewModel, realtimeMapViewModel) }
```

Replace the detail destination with:

```kotlin
composable("map/{slot}") { entry ->
    val slot = CommuteSlot.valueOf(requireNotNull(entry.arguments?.getString("slot")))
    val lifecycleOwner = LocalLifecycleOwner.current
    var fitRouteRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(slot) { realtimeMapViewModel.open(slot) }
    DisposableEffect(lifecycleOwner, slot) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> realtimeMapViewModel.setVisible(true)
                Lifecycle.Event.ON_STOP -> realtimeMapViewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            realtimeMapViewModel.setVisible(true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            realtimeMapViewModel.setVisible(false)
            realtimeMapViewModel.close()
        }
    }
    RealtimeMapScreen(
        state = realtimeState,
        onBack = nav::popBackStack,
        onRetry = realtimeMapViewModel::retry,
        onVehicleSelected = realtimeMapViewModel::selectVehicle,
        onFitRoute = { fitRouteRequest++ },
        mapContent = { mapState, onVehicle ->
            NaverRealtimeMap(mapState, onVehicle, fitRouteRequest)
        },
    )
}
```

Change dashboard `onOpen` to `nav.navigate("map/${it.name}")`. Remove `DetailUiState`, `NamedVehicle`, `loadDetail`, and `RouteDetailScreen` only after the map destination compiles.

- [ ] **Step 4: Bump the app version and replace the obsolete README test flow**

Set:

```kotlin
versionCode = 3
versionName = "0.3.0"
```

Update `README.md` acceptance steps to cover:

```markdown
1. `NAVER_MAP_NCP_KEY_ID`가 설정된 상태로 debug APK를 빌드하고 `adb install -r`로 설치한다.
2. 급행8-1 카드에서 실시간 지도를 열고 저장한 방향만 나타나는지 확인한다.
3. 링크 기반 노선선이 도로망을 따라가고 불연속 구간을 가로지르지 않는지 확인한다.
4. 차량 위치와 수신 시각이 약 8초 간격으로 갱신되는지 확인한다.
5. 네트워크를 끄고 15초 이후 지연 표시, 30초 이후 차량 마커 숨김을 확인한다.
6. 앱을 백그라운드로 보낸 뒤 `getPos02` 호출이 멈추는지 확인한다.
```

- [ ] **Step 5: Run non-destructive full verification**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest
git diff --check
git status --short
```

Expected: all JVM tests PASS, Android test sources compile, both APKs assemble, `git diff --check` is silent, and status contains only intended source/schema/doc changes. Do not run a connected test.

- [ ] **Step 6: Inspect the built artifact and install without clearing app data**

First confirm that `local.properties` contains the key name without printing its value:

```powershell
Get-Content local.properties | Where-Object { $_ -match '^NAVER_MAP_NCP_KEY_ID=' } | ForEach-Object { 'NAVER_MAP_NCP_KEY_ID is configured' }
```

If it is configured and the user confirms the device is connected, run:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: one authorized device and `Success`. `adb install -r` preserves the stored Daegu API key and favorites. If the NCP key is absent, stop before claiming runtime map success and ask the user to add it locally.

- [ ] **Step 7: Perform manual acceptance with the user**

Verify on 급행8-1:

- only the saved direction is shown;
- link geometry follows the route without invented gap bridges;
- markers use confirmed coordinates and snap only on new responses;
- ordinary polling does not reset a camera moved by the user;
- `노선 전체 보기` refits the camera;
- a 30-second stale state hides vehicles;
- backgrounding stops requests;
- NAVER map authentication errors are distinct from Daegu API credential errors.

Run `connectedDebugAndroidTest` only if the user explicitly approves the possible app-data replacement after this warning.

- [ ] **Step 8: Commit the integrated feature**

```powershell
git add app README.md
git commit -m "feat: add realtime bus map"
```

---

## Official NAVER SDK references

- Get started and dependency `3.23.3`: <https://navermaps.github.io/android-map-sdk/guide-en/1.html>
- `MapView` lifecycle contract: <https://navermaps.github.io/android-map-sdk/reference/com/naver/maps/map/MapView.html>
- Authentication failure callback: <https://navermaps.github.io/android-map-sdk/reference/com/naver/maps/map/NaverMapSdk.OnAuthFailedListener.html>
- Path overlay contract: <https://navermaps.github.io/android-map-sdk/reference/com/naver/maps/map/overlay/PathOverlay.html>
