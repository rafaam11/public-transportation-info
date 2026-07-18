# Stop-Centered Daegu Bus Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Every production behavior follows a red-green-refactor cycle.

**Goal:** Replace the commute-slot product with a stop-centered home, nearby/search flows, multi-route precise map, and reliable stop-bound widgets in release 0.7.0.

**Architecture:** Keep Compose/Room/OkHttp/Naver Maps/Glance, but replace slot-keyed state with stable favorite-stop UUIDs. Local Daegu data remains cache-first; only place search crosses a secret-holding Cloudflare Worker.

**Tech Stack:** Kotlin 2.4, Compose Material 3, Room 2.8, OkHttp, Naver Maps 3.23, Glance 1.1, Cloudflare Workers TypeScript, Vitest.

## Global Constraints

- No production code before a failing test.
- No bottom navigation and no periodic background network refresh.
- Keep the public-data API key, Naver map key, Naver Search secrets, and signing secrets out of Git and logs.
- Keep last-known arrivals visible with an explicit timestamp; precise markers are normal through 15 seconds, delayed through 30 seconds, then hidden.
- Ship one 0.7.0 app release after all workstreams pass tests and Samsung Fold acceptance.

### Task 1: Room v4 and stop-centered domain

**Files:** domain models, `BusDatabase.kt`, migrations, local data source, their unit/instrumented tests.

- [x] Add failing domain tests for favorite uniqueness, pinned-route fallback, nearby radius expansion, and stop arrival grouping.
- [x] Add failing migration test for v3→v4 cleanup/preservation.
- [x] Implement `FavoriteStop`, `PinnedRoute`, `StopArrivalGroup`, `NearbyStop`, widget binding entities and repository contracts.
- [ ] Run `./gradlew testDebugUnitTest` and targeted migration instrumentation when device permission is granted.
- [x] Commit the green task.

### Task 2: Daegu APIs, local search, and place Worker

**Files:** remote datasource/parser tests, search repository, `place-search-worker/`.

- [x] Add failing fixtures proving `getBasic02.bs` parsing and stop-only all-route `getRealtime02` grouping.
- [x] Implement catalog persistence, local grouped search, request deduplication, and daily call counting.
- [x] Add failing Vitest cases for query validation, Daegu filtering, WGS84 conversion, cache headers, rate limiting, and upstream failures.
- [x] Implement Worker `/v1/places` with generated bindings and secrets only.
- [x] Run Android unit tests, Worker tests, `tsc`, `wrangler types --check`, and `wrangler deploy --dry-run`.
- [x] Commit the green task.

### Task 3: Single home, grouped search, nearby, and drawer

**Files:** navigation root, home/search/nearby/settings screens and view models, Compose tests.

- [x] Add failing state-reducer and Compose tests for the approved navigation and fallback behaviors.
- [x] Implement the single home and remove commute-slot navigation from reachable UI.
- [x] Implement grouped search, favorite management, optional location permission, and nearby radius fallback.
- [x] Implement drawer settings, diagnostics, releases/notices, and update entry points.
- [ ] Run unit and Compose tests, inspect screenshots at phone and Fold widths, and commit.

### Task 4: Initial camera and multi-route precise tracking

**Files:** precise datasource/session coordinator, realtime map view model, map wrapper/controller, tests.

- [x] Add a failing map-options test proving the initial camera is the selected stop before data arrives.
- [x] Add failing scheduler tests for 3s/8s/30s intervals, concurrency four, target-sequence filtering, cancellation, and partial failure.
- [x] Implement per-route-direction sessions and stop-centered multi-route map state.
- [x] Render route-coded approaching markers, selected-route priority, vehicle focus, and explicit full-route fitting.
- [x] Run all map/unit tests and commit.

### Task 5: Stop-bound Glance widget

**Files:** widget repository, preference replacement, configuration Activity, receiver/worker, manifest/XML, tests.

- [x] Add a failing test reproducing an OEM update before configuration followed by a successful binding.
- [x] Implement Room-observed widget binding, ID-bearing result, immediate update, and unique bootstrap Worker.
- [x] Implement responsive 2/4 route layouts, manual refresh, reconfiguration, and stop-detail deep link.
- [ ] Run widget unit/instrumented tests and commit.

### Task 6: Integration and release

**Files:** version config, README/release notes, integration tests.

- [x] Run `./gradlew testDebugUnitTest assembleDebug lintDebug` and Worker verification.
- [ ] With explicit device-data consent, run connected tests and Samsung Fold acceptance scenarios.
- [x] Verify Git contains no secrets and release APK version is 0.7.0.
- [ ] Deploy Worker secrets and Worker only through secure interactive/CI paths, then build the signed app release.
- [x] Commit release preparation and use the finishing-a-development-branch workflow.
