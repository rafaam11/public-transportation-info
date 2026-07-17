# Daegu Precise Bus Location Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace stop-anchored public positions on the realtime map with confirmed Accubus GPS positions while hiding data older than 30 seconds.

**Architecture:** Keep `getPos02` as the operating-count and recent-stop summary source. Add an in-memory-only precise source that refreshes the Accubus roster every 15 seconds and vehicle details every 3 seconds, exposes only random session keys and confirmed GPS fields, and isolates per-vehicle failures. `RealtimeMapViewModel` combines both sources; no precise identifiers or positions are persisted.

**Tech Stack:** Kotlin, coroutines, OkHttp, Gson, Android ViewModel, Jetpack Compose, NAVER Map SDK, JUnit, MockWebServer.

## Global Constraints

- Never infer, interpolate, predict, or road-snap a vehicle position.
- Use only detail `body.xPos`, `body.yPos`, `body.gpsTm`, and `body.heading` for map placement and heading.
- Treat 0-15 seconds as current, over 15-30 seconds as delayed, and over 30 seconds as hidden, per vehicle.
- Keep Accubus `crfId`, vehicle numbers, and incremental cursors in memory only; never log, display, or persist them.
- On precise-source failure, keep public data for counts and stop text only; never render public coordinates as bus markers.
- Keep traffic-signal UI absent until the official nationwide signal API contains Daegu `stdgCd=2700000000` coverage.
- Release as version name `0.5.0`, version code `5`.

---

### Task 1: Precise domain contract and freshness policy

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/PreciseVehiclePosition.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/domain/PreciseVehiclePositionTest.kt`

**Interfaces:**
- Produces: `PreciseVehiclePosition`, `PreciseVehicleBatch`, `PreciseSourceHealth`, and `PrecisePositionFreshness`.

- [ ] **Step 1: Write failing tests** for per-vehicle current/delayed/hidden classification, future timestamps, Daegu bounds, and route/direction mismatch.
- [ ] **Step 2: Verify RED** with `./gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.domain.PreciseVehiclePositionTest`.
- [ ] **Step 3: Implement the immutable domain types and pure classification/validation functions.**
- [ ] **Step 4: Verify GREEN** with the same command.

### Task 2: Accubus in-memory data source

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/PreciseVehiclePositionDataSource.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/remote/AccubusPreciseRemoteDataSource.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/data/remote/AccubusPreciseRemoteDataSourceTest.kt`

**Interfaces:**
- Consumes: `FavoriteSelection` and a `Clock`.
- Produces: `refreshRoster(selection)`, `refreshPositions(selection)`, and `closeSession()`.

- [ ] **Step 1: Write MockWebServer tests** proving roster direction filtering, detail parsing, HTTP-Date anchored `HHmmss`, partial failures, opaque stable session keys, maximum detail concurrency four, and session clearing.
- [ ] **Step 2: Verify RED** with `./gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.data.remote.AccubusPreciseRemoteDataSourceTest`.
- [ ] **Step 3: Implement roster/detail requests.** Use `/realtime/pos/{routeId}?routeTCd=` and `/realtime/vhcPos/{crfId}`; read the latest confirmed body coordinate and source heading. Keep raw roster identifiers and cursors private.
- [ ] **Step 4: Verify GREEN** with the same command.

### Task 3: Combine public summary and precise positions

**Files:**
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapUiState.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModel.kt`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModelTest.kt`

**Interfaces:**
- Consumes: existing `VehiclePositionDataSource` as summary and new `PreciseVehiclePositionDataSource` as marker truth.
- Produces: total operating count, precise visible list, delayed/hidden count, and source health.

- [ ] **Step 1: Replace existing ViewModel tests with failing tests** for 15-second summary/roster polling, 3-second detail polling, per-vehicle expiry, partial failure isolation, backoff, background cancellation, and `closeSession()`.
- [ ] **Step 2: Verify RED** with `./gradlew.bat :app:testDebugUnitTest --tests com.rafaam11.businfo.ui.RealtimeMapViewModelTest`.
- [ ] **Step 3: Implement independent summary, roster, detail, and freshness jobs.** Reset detail backoff on any success; use 6/15/30-second majority-failure backoff and 15/30/60-second roster backoff.
- [ ] **Step 4: Verify GREEN** with the same command.

### Task 4: Render only confirmed GPS markers

**Files:**
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapUiState.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/RealtimeMapScreen.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayController.kt`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/ui/map/NaverMapOverlayContractTest.kt`
- Modify: `app/src/test/kotlin/com/rafaam11/businfo/ui/RealtimeMapViewModelTest.kt`

**Interfaces:**
- Consumes: precise UI vehicles with source heading and observation age.
- Produces: route-colored side-bus markers, delayed opacity, `전체 운행 n대 · 초정밀 위치 m대`, and no signal controls.

- [ ] **Step 1: Write failing source/UI contract tests** proving map markers come only from precise positions, source heading is used without geometry inference, delayed markers are translucent, and traffic-signal labels are absent.
- [ ] **Step 2: Verify RED** using the two targeted test classes.
- [ ] **Step 3: Update mapper, sheet, and overlay controller.** Missing heading uses zero degrees; no geometry-derived heading.
- [ ] **Step 4: Verify GREEN** using the same command.

### Task 5: Release verification and device install

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`

- [ ] **Step 1: Set version code 5/name 0.5.0 and document the precise-data behavior and acceptance flow.**
- [ ] **Step 2: Run full verification:** `./gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest`.
- [ ] **Step 3: Run `git diff --check` and inspect the complete diff for identifiers, logs, persistence, and unintended signal UI.**
- [ ] **Step 4: Confirm an authorized device with `adb devices` and install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, preserving app data.**
- [ ] **Step 5: Manually verify 급행8-1 road coordinates, roughly 3-second movement, per-vehicle delayed/hide behavior, no stop-coordinate fallback after network loss, and stopped calls in background.**
