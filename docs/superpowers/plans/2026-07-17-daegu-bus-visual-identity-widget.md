# Daegu Bus Visual Identity and Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generic map vehicle markers with route-colored, road-aligned side-view buses, ship a matching adaptive launcher icon, and add a manually refreshed commute widget that opens the selected realtime map.

**Architecture:** Carry the API's nullable `routeTCd` through the route and favorite persistence models, then isolate palette lookup, heading estimation, and Canvas bitmap rendering behind focused map helpers. Reuse rendered marker images and a marker pool on NAVER Map. Build a passive Glance widget over the existing Room-backed dashboard repository, with per-widget SharedPreferences configuration and explicit refresh actions only.

**Tech Stack:** Kotlin 2.4.10, Android SDK 26-37, Jetpack Compose BOM 2026.06.00, Room 2.8.4, NAVER Map SDK 3.23.3, AndroidX Glance AppWidget 1.1.1, JUnit 4, Android instrumentation tests.

## Global Constraints

- Preserve confirmed vehicle coordinates; do not snap, interpolate, or predict positions.
- Hide stale vehicles exactly as the current `RealtimeMapViewModel` does.
- Rotate the side-view bus and its internal route text together; keep the arrival caption horizontal.
- Use `routeTCd`, never route-name prefix parsing, for route color selection.
- Unknown route types use `#306FD9`; no inferred color.
- Widget instances select exactly one of `MORNING` or `EVENING`.
- Widgets never perform periodic background network refresh; `updatePeriodMillis` is `0`.
- A failed widget refresh retains the last normal Room snapshot and shows the failure age.
- Do not add traffic congestion or traffic-signal APIs in this plan.
- Preserve the existing package name `com.rafaam11.businfo`, min SDK 26, and stored user/API-key data.

---

## File Map

**Domain and persistence**

- Modify `app/src/main/kotlin/com/rafaam11/businfo/domain/CommuteDashboardModels.kt`: add nullable `routeTypeCode` to route and favorite models.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`: parse `routeTCd`.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusDatabase.kt`: persist type code, bump schema to 3, and backfill favorites after route replacement.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusDatabaseMigrations.kt`: add `MIGRATION_2_3`.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/data/local/RoomBusLocalDataSource.kt`: map the new fields.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/ui/BusAppViewModel.kt`: copy the selected route type into the favorite.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`: register migration and expose widget dependencies.

**Map identity**

- Create `app/src/main/kotlin/com/rafaam11/businfo/ui/map/RoutePalette.kt`: authoritative type-to-color lookup.
- Create `app/src/main/kotlin/com/rafaam11/businfo/ui/map/VehicleHeadingResolver.kt`: nearest-route-segment rotation calculation.
- Create `app/src/main/kotlin/com/rafaam11/businfo/ui/map/BusMarkerRenderer.kt`: Canvas bitmap renderer and cache.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayController.kt`: marker pooling, route icon, angle, selection styling.

**Launcher identity**

- Create adaptive icon resources under `app/src/main/res/mipmap-anydpi-v26`, `mipmap-anydpi-v33`, `drawable`, and `values`.
- Modify `app/src/main/AndroidManifest.xml`: declare launcher and round icons.

**Widget**

- Modify `gradle/libs.versions.toml` and `app/build.gradle.kts`: Glance 1.1.1.
- Create `app/src/main/kotlin/com/rafaam11/businfo/widget/WidgetPreferenceStore.kt`: per-instance slot/error state.
- Create `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetRepository.kt`: Room/dashboard-to-widget state mapping and explicit refresh.
- Create `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetUpdateNotifier.kt`: notify every widget after a successful foreground arrival refresh.
- Create `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidget.kt`: Glance UI and actions.
- Create `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetReceiver.kt`: platform receiver.
- Create `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetConfigurationActivity.kt`: MORNING/EVENING selection.
- Modify `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt` and `BusInfoApp.kt`: widget-to-map navigation.
- Create `app/src/main/res/xml/commute_widget_info.xml` and widget string resources.
- Modify `app/src/main/AndroidManifest.xml`: receiver and configuration activity.

---

### Task 1: Persist and Propagate the Official Route Type

**Files:**
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/domain/CommuteDashboardModels.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusDatabase.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/local/BusDatabaseMigrations.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/local/RoomBusLocalDataSource.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/BusAppViewModel.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSourceTest.kt`
- Test: `app/src/androidTest/kotlin/com/rafaam11/businfo/data/local/BusDatabaseMigrationTest.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/data/local/RoomBusLocalDataSourceTest.kt`

**Interfaces:**
- Produces: `RouteSummary.routeTypeCode: String?` and `FavoriteSelection.routeTypeCode: String?`.
- Produces: `MIGRATION_2_3: Migration` and database schema version 3.
- Consumes: API field `routeTCd` from `getBasic02`.

- [ ] **Step 1: Add failing remote parsing and favorite persistence tests**

Add a `routeTCd` field to the existing `getBasic02` MockWebServer fixture and assert:

```kotlin
val route = assertIs<RemoteResult.Success<List<RouteSummary>>>(remote.routes("key")).value.single()
assertEquals("1", route.routeTypeCode)
```

Add a Room datasource test that saves and reloads:

```kotlin
val selection = FavoriteSelection(
    CommuteSlot.MORNING, "route", "급행8-1", "0", "유곡리 방면", "stop", "진천역", "1",
)
local.saveFavorite(selection)
assertEquals("1", local.favorite(CommuteSlot.MORNING)?.routeTypeCode)
```

- [ ] **Step 2: Extend the migration test and verify it fails**

Create a version-2 database containing an existing favorite, migrate to 3, then assert both new columns exist and are nullable:

```kotlin
helper.runMigrationsAndValidate("migration-2-3", 3, true, MIGRATION_2_3).use { db ->
    db.query("SELECT routeTypeCode FROM favorites WHERE slot='MORNING'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(0))
    }
}
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*OkHttpDaeguBusRemoteDataSourceTest" --tests "*RoomBusLocalDataSourceTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rafaam11.businfo.data.local.BusDatabaseMigrationTest
```

Expected: compilation or assertion failure because the fields and migration do not exist.

- [ ] **Step 3: Add the domain fields with backward-compatible defaults**

Append the new field to avoid breaking positional fixtures:

```kotlin
data class RouteSummary(
    val routeId: String,
    val routeNo: String,
    val startName: String,
    val endName: String,
    val directionNote: String?,
    val reverseDirectionNote: String?,
    val routeTypeCode: String? = null,
)

data class FavoriteSelection(
    val slot: CommuteSlot,
    val routeId: String,
    val routeNo: String,
    val directionCode: String,
    val directionLabel: String,
    val stopId: String,
    val stopName: String,
    val routeTypeCode: String? = null,
)
```

Set `routeTypeCode = item.string("routeTCd")?.takeIf(String::isNotBlank)` in `parseRoutes`.

- [ ] **Step 4: Add schema version 3 and deterministic backfill**

Add nullable `routeTypeCode` to `RouteEntity` and `FavoriteEntity`, then add:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routes` ADD COLUMN `routeTypeCode` TEXT")
        db.execSQL("ALTER TABLE `favorites` ADD COLUMN `routeTypeCode` TEXT")
    }
}
```

Change the database to `version = 3`. Add this DAO method and call it after inserting the refreshed route catalog:

```kotlin
@Query("""UPDATE favorites
    SET routeTypeCode = (SELECT routeTypeCode FROM routes WHERE routes.routeId = favorites.routeId)
    WHERE routeTypeCode IS NULL""")
abstract suspend fun backfillFavoriteRouteTypes()

@Transaction
open suspend fun replaceRoutes(routes: List<RouteEntity>) {
    clearRoutes()
    insertRoutes(routes)
    backfillFavoriteRouteTypes()
}
```

Register both migrations:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

- [ ] **Step 5: Map the fields and copy the type when saving a stop**

Update every Room mapper to include `routeTypeCode`. In `BusAppViewModel.saveStop` use:

```kotlin
dashboard.saveFavorite(
    FavoriteSelection(
        slot = state.slot,
        routeId = route.routeId,
        routeNo = route.routeNo,
        directionCode = direction.code,
        directionLabel = direction.label,
        stopId = stop.stopId,
        stopName = stop.stopName,
        routeTypeCode = route.routeTypeCode,
    ),
)
```

- [ ] **Step 6: Run focused and full tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rafaam11.businfo.data.local.BusDatabaseMigrationTest
```

Expected: `BUILD SUCCESSFUL`; migration validates schema 3 and keeps the existing favorite.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/domain/CommuteDashboardModels.kt app/src/main/kotlin/com/rafaam11/businfo/data app/src/main/kotlin/com/rafaam11/businfo/ui/BusAppViewModel.kt app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt app/src/test app/src/androidTest app/schemas
git commit -m "feat: persist official route type"
```

---

### Task 2: Resolve Route Colors and Vehicle Heading

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/RoutePalette.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/VehicleHeadingResolver.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/ui/map/RoutePaletteTest.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/ui/map/VehicleHeadingResolverTest.kt`

**Interfaces:**
- Produces: `RoutePaletteResolver.resolve(routeTypeCode: String?): RoutePalette`.
- Produces: `VehicleHeadingResolver.resolve(point: GeoPoint, geometry: RouteGeometry, maxDistanceMeters: Double = 80.0): Float?`.
- Rotation result is NAVER marker angle: east `0f`, south `90f`, west `180f`, north `270f`.

- [ ] **Step 1: Write palette tests**

```kotlin
@Test fun officialTypesUseWebsiteColors() {
    assertEquals(0xFFFF4917.toInt(), RoutePaletteResolver.resolve("1").bodyColor)
    assertEquals(0xFF5BD338.toInt(), RoutePaletteResolver.resolve("2").bodyColor)
    assertEquals(0xFF2C78CF.toInt(), RoutePaletteResolver.resolve("3").bodyColor)
    assertEquals(0xFFFFC000.toInt(), RoutePaletteResolver.resolve("4").bodyColor)
    assertEquals(0xFF4330D6.toInt(), RoutePaletteResolver.resolve("6").bodyColor)
}

@Test fun unknownTypeUsesDaeguBlue() {
    assertEquals(0xFF306FD9.toInt(), RoutePaletteResolver.resolve("G").bodyColor)
}
```

- [ ] **Step 2: Write heading tests**

Cover an eastbound segment, northbound segment, a curved route choosing the nearest segment, and a vehicle 200m away returning null:

```kotlin
assertEquals(0f, resolver.resolve(vehicle, eastbound, 80.0)!!, 0.5f)
assertEquals(270f, resolver.resolve(vehicle, northbound, 80.0)!!, 0.5f)
assertNull(resolver.resolve(farVehicle, eastbound, 80.0))
```

Also reverse the eastbound geometry point order and assert `180f`; this proves route ordering, rather than a guessed road axis, controls the displayed direction.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RoutePaletteTest" --tests "*VehicleHeadingResolverTest"
```

Expected: FAIL because both production types are absent.

- [ ] **Step 3: Implement the palette as one exhaustive table**

```kotlin
data class RoutePalette(val bodyColor: Int, val textColor: Int)

object RoutePaletteResolver {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF131313.toInt()

    fun resolve(routeTypeCode: String?): RoutePalette = when (routeTypeCode) {
        "1" -> RoutePalette(0xFFFF4917.toInt(), WHITE)
        "2" -> RoutePalette(0xFF5BD338.toInt(), BLACK)
        "3" -> RoutePalette(0xFF2C78CF.toInt(), WHITE)
        "4" -> RoutePalette(0xFFFFC000.toInt(), BLACK)
        "5", "6", "7" -> RoutePalette(0xFF4330D6.toInt(), WHITE)
        else -> RoutePalette(0xFF306FD9.toInt(), WHITE)
    }
}
```

- [ ] **Step 4: Implement nearest-segment rotation without moving the point**

For each consecutive geometry point pair, convert longitude/latitude deltas to local meters around the vehicle, clamp projection `t` to `0..1`, and retain the smallest squared distance. Use:

```kotlin
val east = Math.toRadians(b.longitude - a.longitude) * EARTH_RADIUS_METERS * cos(originLatRadians)
val north = Math.toRadians(b.latitude - a.latitude) * EARTH_RADIUS_METERS
val bearing = Math.toDegrees(atan2(east, north))
val markerAngle = ((bearing - 90.0) % 360.0 + 360.0) % 360.0
```

Return `null` when the nearest projected distance exceeds `maxDistanceMeters`. Ignore zero-length segments. Do not mutate `point` or `geometry`.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RoutePaletteTest" --tests "*VehicleHeadingResolverTest"
git add app/src/main/kotlin/com/rafaam11/businfo/ui/map app/src/test/kotlin/com/rafaam11/businfo/ui/map
git commit -m "feat: resolve route colors and vehicle heading"
```

Expected: focused tests pass.

---

### Task 3: Render and Display Side-View Vehicle Markers

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/BusMarkerRenderer.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayController.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/NaverRealtimeMap.kt`
- Test: `app/src/androidTest/kotlin/com/rafaam11/businfo/ui/map/BusMarkerRendererTest.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayContractTest.kt`

**Interfaces:**
- Produces: `BusMarkerRenderer.render(routeNo, palette, selected, density): Bitmap`.
- Produces: `BusMarkerIconCache.icon(routeNo, palette, selected, density): OverlayImage`.
- Consumes: `state.selection.routeNo`, `routeTypeCode`, `state.geometry`, and `state.visibleVehicles`.

- [ ] **Step 1: Write renderer boundary tests**

On-device tests render `급행8-1`, `814`, and `555-7`, then assert the bitmap is non-empty, exactly `72x34dp` for normal and approximately 15% larger for selected, and contains non-transparent pixels inside all four edges without clipping:

```kotlin
val bitmap = renderer.render("급행8-1", RoutePaletteResolver.resolve("1"), false, density)
assertEquals((72 * density).roundToInt(), bitmap.width)
assertEquals((34 * density).roundToInt(), bitmap.height)
val pixels = IntArray(bitmap.width * bitmap.height)
bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
assertTrue(pixels.count { Color.alpha(it) > 0 } > bitmap.width * bitmap.height / 4)
```

Render a synthetic overlong route name and assert the full input is retained in the renderer result metadata and exposed as the horizontal caption fallback. Preserve the approved official body/text color table exactly even where its numeric WCAG ratio is below `4.5`; render a thin contrasting text outline or shadow instead of changing or rejecting official colors. Call the cache twice with an identical key and assert the same `OverlayImage` instance is reused.

Add a source contract test requiring `OverlayImage.fromBitmap`, `marker.angle`, `marker.isFlat = true`, and no coordinate reassignment through a projection/snapping helper.

The overlay contract also covers selected size/z-index, horizontal caption configuration, and the current stale branch producing zero visible vehicle markers. Do not weaken the stale policy in order to test the renderer.

Add one renderer-cache failure test with a throwing fake renderer and assert the returned image is the existing `R.drawable.ic_bus_marker` fallback. This prevents a single Canvas/font failure from removing every live vehicle from the map.

- [ ] **Step 2: Run tests to verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*NaverMapOverlayContractTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rafaam11.businfo.ui.map.BusMarkerRendererTest
```

Expected: FAIL because the Canvas renderer does not exist.

- [ ] **Step 3: Implement Canvas rendering and font fitting**

Render into a transparent ARGB bitmap. Convert dp with the supplied density. Draw in this order: shadow, rounded body, pale windows, three window dividers, two wheels, route text. Fit text with this loop so suffixes are never truncated:

```kotlin
var textSize = 15f * density
val maxTextWidth = width - 20f * density
paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
while (paint.measureText(routeNo) > maxTextWidth && textSize > 8f * density) {
    textSize -= density
    paint.textSize = textSize
}
```

Use the exact palette text color for the fill, center alignment, anti-aliasing, and a thin contrasting text outline or shadow for legibility. Draw a selected white stroke before filling the body. Keep all dimensions in named dp constants inside `BusMarkerRenderer`.

- [ ] **Step 4: Add an LRU icon cache**

Use a 32-entry `LruCache<BusMarkerKey, OverlayImage>`. `BusMarkerKey` contains route number, body color, text color, selected flag, and integer density DPI. On miss, render once and wrap with `OverlayImage.fromBitmap`. Expose `evictAll()` for map disposal.

Bitmap generation must fall back to the existing marker resource instead of failing the map:

```kotlin
val icon = runCatching {
    OverlayImage.fromBitmap(renderer.render(routeNo, palette, selected, density))
}.getOrElse {
    OverlayImage.fromResource(R.drawable.ic_bus_marker)
}
put(key, icon)
return icon
```

- [ ] **Step 5: Refactor the overlay controller to reuse a marker pool**

Replace clear/recreate behavior for vehicles with `ensureVehicleMarkerCount(count)`. For each sorted vehicle index update the existing marker:

```kotlin
marker.position = vehicle.point.toLatLng()
marker.icon = iconCache.icon(routeNo, palette, vehicle.key == state.selectedVehicleKey, density)
marker.angle = headingResolver.resolve(vehicle.point, geometry) ?: 0f
marker.isFlat = true
marker.captionText = arrivalCaption(vehicle.remainingStops)
marker.tag = vehicle.key
marker.zIndex = if (vehicle.key == state.selectedVehicleKey) 20 else 10
marker.map = map
```

Pass Android display density from `NaverRealtimeMap`. Remove extra pooled markers when vehicle count shrinks. Keep stop/path behavior unchanged. `clear()` removes all overlays and calls `iconCache.evictAll()`.

- [ ] **Step 6: Run focused, full, and device tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rafaam11.businfo.ui.map.BusMarkerRendererTest
.\gradlew.bat :app:assembleDebug
```

Expected: all commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/ui/map app/src/test/kotlin/com/rafaam11/businfo/ui/map app/src/androidTest/kotlin/com/rafaam11/businfo/ui/map
git commit -m "feat: render route-colored side bus markers"
```

---

### Task 4: Ship the Adaptive Launcher Icon

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/LauncherIconContractTest.kt`

**Interfaces:**
- Produces: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` with monochrome support on API 33+.

- [ ] **Step 1: Write the resource contract test**

Assert the manifest contains `android:icon` and `android:roundIcon`, v26 adaptive icons reference foreground/background, and v33 icons additionally reference monochrome. Assert the foreground vector contains the three official accent colors `#FF4917`, `#5BD338`, and `#FFC000`.

- [ ] **Step 2: Run the contract test and verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*LauncherIconContractTest"
```

Expected: FAIL because launcher resources are absent.

- [ ] **Step 3: Create the icon vectors**

Use a `108x108` viewport. Keep the bus within the adaptive safe zone (`18..90`). Draw a white side body from approximately `(19,36)` to `(89,73)`, pale blue windows, dark wheels, and three short livery stripes under the windows. The monochrome vector uses the same bus silhouette as one opaque path without colored stripes.

Create `ic_launcher_background.xml` as:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#0D5E9C" />
</shape>
```

Create the foreground vector with these exact layers:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M24,33H80Q89,33 89,42V68H19V40Q19,33 24,33Z" />
    <path android:fillColor="#FFDDF1F7"
        android:pathData="M27,39H79Q83,39 83,43V52H25V41Q25,39 27,39Z" />
    <path android:fillColor="#FF0D5E9C"
        android:pathData="M44,39H47V52H44ZM63,39H66V52H63Z" />
    <path android:fillColor="#FFFF4917" android:pathData="M25,56H44V61H25Z" />
    <path android:fillColor="#FF5BD338" android:pathData="M44,56H64V61H44Z" />
    <path android:fillColor="#FFFFC000" android:pathData="M64,56H83V61H64Z" />
    <path android:fillColor="#FF17212B"
        android:pathData="M32,63m-7,0a7,7 0,1 0,14,0a7,7 0,1 0,-14,0M76,63m-7,0a7,7 0,1 0,14,0a7,7 0,1 0,-14,0" />
    <path android:fillColor="#FFE8EEF1"
        android:pathData="M32,63m-3,0a3,3 0,1 0,6,0a3,3 0,1 0,-6,0M76,63m-3,0a3,3 0,1 0,6,0a3,3 0,1 0,-6,0" />
</vector>
```

Create `ic_launcher_monochrome.xml` as:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FF000000"
        android:pathData="M24,33H80Q89,33 89,42V68H19V40Q19,33 24,33ZM32,63m-7,0a7,7 0,1 0,14,0a7,7 0,1 0,-14,0M76,63m-7,0a7,7 0,1 0,14,0a7,7 0,1 0,-14,0" />
</vector>
```

It intentionally omits windows and colored stripes so themed launchers receive a clean mask.

The API 33 adaptive icon must be:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

The v26 variant omits `<monochrome>`.

- [ ] **Step 4: Wire the manifest and verify packaging**

Add:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*LauncherIconContractTest"
.\gradlew.bat :app:assembleDebug
```

Expected: test and packaging pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res app/src/main/AndroidManifest.xml app/src/test/kotlin/com/rafaam11/businfo/LauncherIconContractTest.kt
git commit -m "feat: add adaptive side bus launcher icon"
```

---

### Task 5: Add Widget Dependencies, Persistence, and State Mapping

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/widget/WidgetPreferenceStore.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetRepository.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetUpdateNotifier.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/data/DashboardRepository.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/widget/CommuteWidgetRepositoryTest.kt`

**Interfaces:**
- Produces: `WidgetPreferenceStore.slot(appWidgetId): CommuteSlot?`, `saveSlot`, `errorState`, `saveError`, `clear`.
- Produces: `CommuteWidgetRepository.state(appWidgetId, now): CommuteWidgetUiState` and `refresh(appWidgetId, onStarted): WidgetRefreshResult`.
- Produces: `DashboardUpdateNotifier.notifyChanged()`; a successful arrival write calls it once.
- Uses stable `androidx.glance:glance-appwidget:1.1.1`.

- [ ] **Step 1: Add the stable Glance dependency**

Add:

```toml
glance = "1.1.1"
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
```

and `implementation(libs.glance.appwidget)`.

- [ ] **Step 2: Write repository tests with fake stores**

Cover configured/empty, configured/arrival, deleted favorite requiring reconfiguration, refresh success, refresh failure retaining prior snapshot, missing API key, and two concurrent refresh calls for the same widget. Use a fake dashboard `Flow` and a fake preference store. Assert:

```kotlin
assertEquals("급행8-1", state.routeNo)
assertEquals("도착 임박", state.primaryText)
assertEquals("1", state.routeTypeCode)
assertEquals(CommuteSlot.MORNING, state.slot)
```

On failure assert the same route/arrival remains and `refreshError == BusDataError.ServiceUnavailable`.

Block the fake remote during the first refresh, invoke a second refresh for the same ID, and assert the dashboard receives exactly one refresh call and the second result is `WidgetRefreshResult.AlreadyRunning`.

- [ ] **Step 3: Run tests to verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CommuteWidgetRepositoryTest"
```

Expected: FAIL because widget repository types are absent.

- [ ] **Step 4: Implement per-instance preferences**

Store only widget-specific data in `SharedPreferences("commute-widget")`:

```kotlin
interface WidgetPreferenceGateway {
    fun slot(appWidgetId: Int): CommuteSlot?
    fun saveSlot(appWidgetId: Int, slot: CommuteSlot)
    fun errorState(appWidgetId: Int): WidgetRefreshError?
    fun saveError(appWidgetId: Int, error: BusDataError?, atEpochMillis: Long?)
    fun clear(appWidgetId: Int)
}

data class WidgetRefreshError(val error: BusDataError, val atEpochMillis: Long)
```

Keys are `slot:<id>`, `error:<id>`, and `errorAt:<id>`. Parse enum values with `runCatching`; a missing or malformed error/time pair returns null and is removed.

- [ ] **Step 5: Implement passive widget state mapping**

`CommuteWidgetUiState` contains `appWidgetId`, `slot`, `routeNo`, `routeTypeCode`, `stopName`, `directionLabel`, `primaryText`, `secondaryText`, `fetchedAt`, `refreshError`, `refreshErrorAt`, `isRefreshing`, and `requiresConfiguration`. `state()` takes one value from `dashboard.observeDashboard().first()`, matches the configured slot, and never calls the network. A missing slot or a configured slot whose favorite was deleted sets `requiresConfiguration = true`; an existing favorite with no arrival snapshot displays `아직 받은 정보 없음`.

Use `ConcurrentHashMap<Int, Mutex>` to serialize refreshes per widget. `refresh(appWidgetId, onStarted)` returns the following sealed result and uses `mutex.tryLock()` so a duplicate tap returns immediately:

```kotlin
sealed interface WidgetRefreshResult {
    data object Success : WidgetRefreshResult
    data object AlreadyRunning : WidgetRefreshResult
    data class Failed(val error: BusDataError) : WidgetRefreshResult
    data object RequiresConfiguration : WidgetRefreshResult
}
```

After acquiring the lock, add the ID to a thread-safe `refreshingIds` set, invoke `onStarted`, load the configured slot, and call only `dashboard.refreshFavorite(slot)`. Store a non-null error with `clock.instant().toEpochMilli()`; success clears both error keys. In `finally`, remove the ID and unlock. It never deletes or replaces a Room snapshot on failure. `state()` derives `isRefreshing` from `refreshingIds`.

Add this dependency boundary to `DashboardRepository`:

```kotlin
fun interface DashboardUpdateNotifier {
    suspend fun notifyChanged()

    companion object { val NONE = DashboardUpdateNotifier {} }
}
```

Give `DashboardRepository` the default constructor argument `updateNotifier: DashboardUpdateNotifier = DashboardUpdateNotifier.NONE`. Immediately after a successful `local.saveArrival(...)`, call `updateNotifier.notifyChanged()`. `CommuteWidgetUpdateNotifier` implements the interface by calling `CommuteWidget().updateAll(context)`. AppGraph injects it; existing repository tests use the default no-op.

- [ ] **Step 6: Expose the repository from AppGraph and run tests**

Construct `WidgetPreferenceStore(context.applicationContext)` and `CommuteWidgetRepository(dashboardRepository, widgetPreferences, Clock.systemUTC())` as graph properties.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CommuteWidgetRepositoryTest"
.\gradlew.bat :app:assembleDebug
```

Expected: focused tests and build pass.

- [ ] **Step 7: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt app/src/main/kotlin/com/rafaam11/businfo/data/DashboardRepository.kt app/src/main/kotlin/com/rafaam11/businfo/widget app/src/test/kotlin/com/rafaam11/businfo/widget
git commit -m "feat: add commute widget data layer"
```

---

### Task 6: Build, Configure, and Navigate from the Commute Widget

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidget.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetReceiver.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/widget/CommuteWidgetConfigurationActivity.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt`
- Create: `app/src/main/res/xml/commute_widget_info.xml`
- Create: `app/src/main/res/values/widget_strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/widget/CommuteWidgetContractTest.kt`
- Test: `app/src/androidTest/kotlin/com/rafaam11/businfo/widget/CommuteWidgetConfigurationTest.kt`

**Interfaces:**
- Produces: `CommuteWidget : GlanceAppWidget` and `CommuteWidgetReceiver : GlanceAppWidgetReceiver`.
- Produces action callbacks `RefreshCommuteWidgetAction` and `OpenCommuteMapAction`.
- Produces MainActivity extra `EXTRA_OPEN_MAP_SLOT` with enum name.
- Produces MainActivity extra `EXTRA_OPEN_KEY_SETTINGS` for invalid-credential recovery.

- [ ] **Step 1: Write manifest and provider contract tests**

Assert:

- receiver handles `android.appwidget.action.APPWIDGET_UPDATE`;
- provider XML has `updatePeriodMillis="0"`, `configure`, `resizeMode`, and `widgetCategory="home_screen"`;
- configuration activity is exported so the launcher can open it, while the receiver is not exported;
- MainActivity declares and consumes `EXTRA_OPEN_MAP_SLOT` and `EXTRA_OPEN_KEY_SETTINGS`.

- [ ] **Step 2: Write the configuration activity UI test**

Launch with an app widget ID, tap `출근`, and assert `RESULT_OK`, the same ID extra, saved MORNING slot, and an initial widget update request. Repeat for `퇴근`.

- [ ] **Step 3: Run tests to verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CommuteWidgetContractTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rafaam11.businfo.widget.CommuteWidgetConfigurationTest
```

Expected: FAIL because widget components are absent.

- [ ] **Step 4: Implement the Glance widget**

Use `SizeMode.Responsive` for compact and expanded sizes. Render a rounded off-white card with:

- top row: slot label and freshness/error text;
- route row: route-colored pill and route number;
- large arrival primary text;
- stop and direction;
- explicit `새로고침` action.

Use `RoutePaletteResolver` colors converted to Glance `ColorProvider`. The card click starts MainActivity with `EXTRA_OPEN_MAP_SLOT`; the refresh text runs `RefreshCommuteWidgetAction`. Do not use Compose UI composables inside Glance.

When `refreshError == BusDataError.InvalidCredential`, replace the normal refresh label with `API 키 변경`; its click starts MainActivity with `EXTRA_OPEN_KEY_SETTINGS=true`. Other failures keep the normal card-open and refresh actions.

`RefreshCommuteWidgetAction.onAction` must:

```kotlin
val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
AppGraph.get(context).commuteWidgetRepository.refresh(appWidgetId) {
    CommuteWidget().update(context, glanceId)
}
CommuteWidget().update(context, glanceId)
```

Use `GlanceAppWidgetManager.getAppWidgetId(glanceId)` everywhere a platform ID is required; do not cast the opaque `GlanceId`.

- [ ] **Step 5: Implement configuration and deletion cleanup**

The configuration activity reads `AppWidgetManager.EXTRA_APPWIDGET_ID`, defaults to `RESULT_CANCELED`, shows two Material buttons, saves the chosen slot, calls `CommuteWidget().update(context, glanceId)`, sets `RESULT_OK`, and finishes. Receiver `onDeleted` clears preference keys for every deleted ID. When `requiresConfiguration` is true, the widget shows `카드를 다시 설정해 주세요` and a `설정` action that opens this same activity with the current `EXTRA_APPWIDGET_ID`.

- [ ] **Step 6: Add widget-to-map navigation**

In MainActivity, expose a `MutableStateFlow<CommuteSlot?>` and `MutableStateFlow<Boolean>` initialized from the launch extras and update both in `onNewIntent`. Pass both to `BusInfoApp`. When `openKeySettings` is true, call `viewModel.clearKey()` and consume the flag so the key-entry screen appears. In `BusInfoApp`, once `AppUiState.Ready` and `nav` exist:

```kotlin
LaunchedEffect(openMapSlot) {
    openMapSlot?.let { slot ->
        nav.navigate("map/${slot.name}") { launchSingleTop = true }
        onOpenMapSlotConsumed()
    }
}
```

If the key is absent, retain the pending slot until key validation reaches Ready. If the selected favorite no longer exists, the existing map error state handles it without inventing data.

- [ ] **Step 7: Declare the provider**

Use a provider XML with `minWidth="180dp"`, `minHeight="110dp"`, `targetCellWidth="3"`, `targetCellHeight="2"`, `resizeMode="horizontal|vertical"`, `widgetCategory="home_screen"`, `updatePeriodMillis="0"`, `initialLayout="@layout/glance_default_loading_layout"`, and `configure="com.rafaam11.businfo.widget.CommuteWidgetConfigurationActivity"`.

Declare the components exactly as follows; do not add `BIND_APPWIDGET` to either component:

```xml
<activity
    android:name=".widget.CommuteWidgetConfigurationActivity"
    android:exported="true" />
<receiver
    android:name=".widget.CommuteWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/commute_widget_info" />
</receiver>
```

- [ ] **Step 8: Run tests and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rafaam11.businfo.widget.CommuteWidgetConfigurationTest
.\gradlew.bat :app:assembleDebug
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add configurable commute widget"
```

Expected: tests and debug build pass.

---

### Task 7: Final Integration, Version, and Real-Device Acceptance

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md` if it contains the current feature list or install instructions.
- Test: all existing unit and Android tests.

**Interfaces:**
- Produces: debug APK version `0.4.0` (`versionCode = 4`).
- Produces: verified installed behavior on the connected SM_F936N or the currently authorized test device.

- [ ] **Step 1: Add a final feature contract test**

Extend `FoundationContractTest` to assert the project contains route palette, Canvas marker renderer, adaptive icon, Glance receiver/provider, `updatePeriodMillis="0"`, and widget map-open navigation. This test protects packaging wiring, not visual pixel correctness.

- [ ] **Step 2: Run it before the version bump**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FoundationContractTest"
```

Expected: PASS after Tasks 1-6; if it fails, fix only the missing integration it identifies.

- [ ] **Step 3: Bump the application version and update durable docs**

Set:

```kotlin
versionCode = 4
versionName = "0.4.0"
```

Update README feature and test instructions only where they are present; do not add release automation or the deferred traffic features.

- [ ] **Step 4: Run the full verification suite**

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest assembleDebug
git diff --check
```

Expected: `BUILD SUCCESSFUL`, no failed tests, and no whitespace errors.

- [ ] **Step 5: Install without clearing user data**

Resolve ADB from `sdk.dir` and run:

```powershell
& "$sdkDir\platform-tools\adb.exe" devices -l
& "$sdkDir\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: exactly one authorized `device` and install output `Success`.

- [ ] **Step 6: Verify the original realtime map surface**

Open the saved 급행8-1 commute map and confirm:

1. normal vehicles use `#FF4917` side-view buses;
2. every bus contains the full `급행8-1` text;
3. bus and internal text rotate with nearby route geometry;
4. GPS coordinates are unchanged;
5. arrival captions remain horizontal;
6. selection enlarges only the selected vehicle;
7. a forced stale state hides vehicles;
8. Logcat contains no NAVER auth error, fatal exception, or bitmap failure.

Capture a screenshot for evidence.

- [ ] **Step 7: Verify launcher and widget**

Confirm the new launcher icon appears after launcher refresh. Add two widget instances, configure one 출근 and one 퇴근, then verify:

- correct route color and latest Room value;
- refresh performs one request and updates age;
- network-off refresh keeps the last value and shows failure;
- tapping the card opens the matching realtime map;
- waiting 15+ minutes does not trigger automatic widget API traffic.

Capture screenshots of the launcher, normal widget, and failed-refresh widget.

- [ ] **Step 8: Commit final integration**

```powershell
git add app/build.gradle.kts README.md app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt
git commit -m "chore: release visual identity and widget v0.4.0"
```

- [ ] **Step 9: Review the complete branch**

```powershell
git status --short
git log --oneline --decorate -10
git diff 70c71ef..HEAD --stat
```

Expected: clean worktree and only the planned visual identity, widget, version, test, schema, and documentation changes.

---

## References

- Approved design: `docs/superpowers/specs/2026-07-17-daegu-bus-visual-identity-widget-design.md`
- NAVER Map Android SDK marker guide: <https://navermaps.github.io/android-map-sdk/guide-ko/5-2.html>
- Android Glance widget creation: <https://developer.android.com/develop/ui/compose/glance/create-app-widget>
- Android Glance update/state guidance: <https://developer.android.com/develop/ui/compose/glance/glance-app-widget>
- Android Glance stable release 1.1.1: <https://developer.android.com/jetpack/androidx/releases/glance>
- Widget configuration activity guidance: <https://developer.android.com/develop/ui/compose/glance/configuration>
