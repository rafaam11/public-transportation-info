# Final Review Fix Report

## Status

All four Important final-review findings were addressed on `feat/live-vehicle-list` from base `6f90c4440e5b0bd325f386305f1f44e76277c2ee`.

## Environment

- `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`
- `ANDROID_HOME=C:\Users\uiop3\AppData\Local\Android\Sdk`
- `GRADLE_OPTS=-Xmx768m -XX:MaxMetaspaceSize=384m`
- Gradle invocations used `--no-daemon --max-workers=1`.

## RED evidence

Tests were added before production changes.

Focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSourceTest" --tests "com.rafaam11.businfo.FoundationContractTest" --tests "com.rafaam11.businfo.data.BusRepositoryTest" --no-daemon --max-workers=1
```

- 21 tests ran and 3 failed against the original production implementation.
- The wholly invalid nonempty vehicle array test expected `Failure(MalformedResponse)` but observed `Success([])`.
- After correcting only the cancellation test's coroutine scheduling, the real delayed MockWebServer call took the old timeout path instead of promptly releasing the single-slot OkHttp dispatcher; the cancellation assertion failed as intended.
- After correcting only missing-resource handling in the static test, the non-saveable draft / backup exclusion contract failed with an assertion as intended.
- The repository malformed-refresh retention test passed as a control because repository failures already preserve the last successful batch.

The first cancellation test attempt blocked the `runBlocking` event loop before its launched request could start, and the first backup contract attempt threw on absent files. Those were test-harness errors, so both tests were corrected and rerun to obtain behavior-specific assertion failures before production changes.

## GREEN implementation

- Replaced blocking `Call.execute()` transport with `enqueue()` bridged through `suspendCancellableCoroutine`.
- Registered `invokeOnCancellation { call.cancel() }` before enqueueing, guarded callback resume with continuation activity, and closed the response in exactly one completion/cancellation path.
- Left coroutine cancellation uncaught while retaining the existing `IOException` and JSON parsing mappings.
- Kept `items: []` as a successful empty result, retained valid rows from mixed arrays, and classified nonempty arrays with zero valid rows as `MalformedResponse`.
- Changed the API-key draft to `remember`, preserving it only for the current composition.
- Added legacy full-backup and Android 12+ cloud/device-transfer exclusions for `credentials.xml`, wired through the manifest.
- Added direct `VehicleListScreen` Compose tests without constructing `AppGraph` or any production network dependency.

## Verification

Focused GREEN:

- Command: the focused command shown above.
- Result: 21/21 passed.

Complete app unit suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1
```

- Result: 55/55 passed.

API-probe suite:

```powershell
.\gradlew.bat :api-probe:test --rerun-tasks --no-daemon --max-workers=1
```

- Result: 19/19 passed.

Builds, run separately:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --max-workers=1
```

- `assembleDebug`: successful.
- `assembleDebugAndroidTest`: successful; all 4 instrumentation test methods compiled and the instrumentation APK assembled.
- `adb devices` returned no attached emulator/device. Instrumentation tests were compiled but not executed; no runtime-pass claim is made.

Static/privacy checks:

- `git diff --check`: clean (only Windows LF-to-CRLF checkout notices).
- Production-domain scan for `vhc`, `vehicleNo`, `vehicleNumber`, and `licensePlate`: no matches.
- Added-diff credential-literal scan: no matches.
- No `.local/api-probe/reports` directory existed to scan in this worktree.
- The contract test verifies no `rememberSaveable` remains in the credential screen, both manifest resources are wired, the legacy rules exclude `credentials.xml`, and both data-extraction sections exclude it.

## APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 12,820,330 bytes
- SHA-256: `42B60CEF4AB84029B3D9F16C456564BF83D695AA654A9EA971DDD4AECD72FEE2`

## Files

- `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`
- `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSourceTest.kt`
- `app/src/test/kotlin/com/rafaam11/businfo/data/BusRepositoryTest.kt`
- `app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt`
- `app/src/androidTest/kotlin/com/rafaam11/businfo/ui/VehicleListScreenTest.kt`
- `.superpowers/sdd/final-fix-report.md`

## Self-review

- The cancellation regression uses a real MockWebServer response delayed for 3 seconds and an OkHttp dispatcher limited to one request. The client retains its 10-second call timeout; cancellation must return in under 2 seconds and the next request must reach the server within 2 seconds.
- Callback failure does not resume a cancelled continuation. Callback success either transfers the response to the active continuation with cancellation cleanup or closes it immediately when inactive. Normal parsing remains inside `Response.use`.
- No API envelope classification, query construction, display-safe vehicle model, retained-data behavior, or credential logging behavior was broadened.
- Empty success remains cache-replacing, while wholly malformed refreshes remain failures and therefore preserve retained content.
- Backup remains enabled for ordinary app data; only the credential preferences file is excluded.
- The Compose suite uses visible text semantics because those are sufficient; no production test tags or production graph seams were added.

## Concerns

- No emulator/device was attached, so the Compose instrumentation suite was not executed at runtime. Compilation and instrumentation APK assembly succeeded.
- `createComposeRule` emits an upstream deprecation warning recommending the v2 API, but the brief explicitly requested `createComposeRule`; this does not fail compilation.

## R2 Final Review Fixes

### Status

Both remaining R2 Important findings were fixed on top of commit `e16a51c79d1faa72543401a716552794d4d1d435`.

### RED evidence

Before production or dependency changes, added:

- a real MockWebServer regression with immediate response headers and a 32 KiB body throttled at 1 KiB per 250 ms;
- an OkHttp `EventListener` assertion proving the underlying call is cancelled during body consumption;
- prompt cancellation and follow-up-request assertions using a one-slot OkHttp dispatcher and the existing 10-second call timeout;
- a static contract requiring the BOM-managed `ui-test-manifest` catalog alias and `debugImplementation` dependency.

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSourceTest.cancellingDuringResponseBodyCancelsCallAndFreesDispatcherForNextRequest" --tests "com.rafaam11.businfo.FoundationContractTest.composeInstrumentationHasBomManagedTestActivityHost" --no-daemon --max-workers=1
```

Result: 2/2 tests failed by assertion against the prior implementation. Body-phase cancellation did not promptly cancel the call, and the test-host dependency contract was absent.

### GREEN implementation

- The cancellable suspended operation now owns `Response.use`, response-body reading, JSON parsing, envelope validation, and remote-result mapping inside the OkHttp callback worker.
- `invokeOnCancellation { call.cancel() }` remains registered while body consumption is in progress.
- The callback closes every received response through one `Response.use` path, does not resume inactive continuations, maps body I/O/JSON failures as before, and has one terminal resume path per callback.
- The caller now receives only a fully parsed `RemoteResult<JsonElement>`; body reading and Gson work no longer run on the ViewModel Main context.
- Added BOM-managed `androidx.compose.ui:ui-test-manifest` as `debugImplementation` through the version catalog.

The first GREEN compile exposed a missing `kotlin.coroutines.resume` import. After adding the import, the focused suite passed; a callback parameter-name warning was then removed before fresh verification.

### Fresh verification

All commands used the JDK/SDK/low-memory environment documented above.

- Transport focused: `:app:testDebugUnitTest --tests "com.rafaam11.businfo.data.remote.OkHttpDaeguBusRemoteDataSourceTest"` — 12/12 passed.
- Complete app unit suite: `:app:testDebugUnitTest` — 57/57 passed.
- API-probe: `:api-probe:test --rerun-tasks` — 19/19 passed.
- `:app:assembleDebug` — successful.
- `:app:assembleDebugAndroidTest` — successful.
- The merged and packaged debug manifests contain `androidx.activity.ComponentActivity` contributed by the Compose test manifest.
- `adb devices` returned no attached emulator/device: 4 instrumentation test methods compiled and have a runnable host, but 0 were executed. No runtime-pass claim is made.
- `git diff --check` — clean apart from Windows LF-to-CRLF notices.
- Production-domain identifier scan — no matches.
- Added-diff credential-literal scan — no matches.

### R2 APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 12,347,031 bytes
- SHA-256: `C45BDF997D66FADE0B0A8204F5479DB74A1DFD8BE37DBD12268608C6F661CAAB`

### R2 files

- `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`
- `app/src/test/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSourceTest.kt`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt`
- `.superpowers/sdd/final-fix-report.md`

### R2 self-review and concerns

- Header-delay cancellation coverage remains intact; the new regression covers the distinct post-header body phase.
- Cancellation is observed by OkHttp's real `EventListener`, returns in under 2 seconds, and lets the follow-up request reach the server within 2 seconds instead of waiting for the 10-second timeout.
- Parsing preserves prior HTTP, envelope, malformed-array, and privacy behavior while moving work off Main.
- No framework was added; the host artifact is the official Compose BOM-managed test manifest.
- No device was available, so instrumentation runtime behavior remains unexecuted. The test sources, host activity manifest, and instrumentation APK were verified.
- `createComposeRule` still emits its non-failing upstream deprecation warning, as noted above.
