# Daegu Bus Live Vehicle List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder screen with API-key onboarding and a resilient, manually refreshed live vehicle list for Daegu route 814.

**Architecture:** A Compose screen consumes immutable state from `VehicleListViewModel`; the ViewModel coordinates `BusRepository`, which owns the last successful in-memory snapshot and calls an OkHttp/Gson `DaeguBusRemoteDataSource`. `CredentialStore` persists only the service key in app-private preferences. This is the first end-to-end slice of the approved full MVP; Room, commute cards, NAVER Map, lifecycle polling, stale map markers, and Glance remain separate follow-up specs so this slice is independently installable and testable.

**Tech Stack:** Kotlin 2.4.10, Android Gradle Plugin 9.3.0, Java 17, Jetpack Compose BOM 2026.06.00, Lifecycle 2.11.0, kotlinx-coroutines 1.11.0, OkHttp 4.12.0, Gson 2.13.2, JUnit 4, MockWebServer.

## Global Constraints

- Package and application ID remain exactly `com.rafaam11.businfo`.
- `minSdk=26`, `targetSdk=37`, `compileSdk=37`, Java 17.
- Base URL is exactly `https://apis.data.go.kr/6270000/dbmsapi02`.
- Key validation calls `getBasic02` with only `serviceKey`.
- Vehicle refresh calls `getPos02` with `serviceKey` and `routeId=3000814001`.
- A response succeeds only when HTTP is successful, `header.resultCode=0000`, and `header.success=true`.
- `xPos` maps to longitude and `yPos` maps to latitude.
- `vhcNo2` must not exist in persistent models, domain models, UI state, displayed text, or logs.
- Do not log a service key, full request URL, or raw response body.
- Freshness boundaries stay `age <= 15s`, `15s < age <= 30s`, and `age > 30s`.
- This slice performs only user-requested refreshes; it does not poll automatically.
- A failed refresh preserves the last successful in-memory list.
- A successful empty response clears the currently operating list and renders `현재 운행 차량 없음`.
- All Gradle verification runs use one worker and in-process Kotlin compilation because this Windows host has a small pagefile.

---

## Planned File Structure

- `gradle/libs.versions.toml`: add Lifecycle and coroutine dependency aliases.
- `app/build.gradle.kts`: consume network, lifecycle, and coroutine dependencies already available or newly aliased.
- `app/src/main/kotlin/com/rafaam11/businfo/data/credential/CredentialStore.kt`: app-facing credential contract.
- `app/src/main/kotlin/com/rafaam11/businfo/data/credential/SharedPreferencesCredentialStore.kt`: app-private Android implementation.
- `app/src/main/kotlin/com/rafaam11/businfo/data/remote/DaeguBusRemoteDataSource.kt`: remote contract and transport error types.
- `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`: URL construction, HTTP execution, envelope validation, and JSON parsing.
- `app/src/main/kotlin/com/rafaam11/businfo/domain/VehicleSnapshot.kt`: sanitized vehicle domain model.
- `app/src/main/kotlin/com/rafaam11/businfo/domain/VehicleLoadResult.kt`: success/failure result contract.
- `app/src/main/kotlin/com/rafaam11/businfo/data/BusRepository.kt`: key validation and last-successful snapshot policy.
- `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListUiState.kt`: immutable rendering state.
- `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListViewModel.kt`: event serialization and state transitions.
- `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListScreen.kt`: key form, summary, errors, and vehicle list.
- `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`: manual dependency assembly.
- `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`: ViewModel creation and Compose host.
- `app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt`: Material theme and screen binding.
- Matching unit tests under `app/src/test/kotlin/com/rafaam11/businfo/...`.

### Task 1: Add Runtime Dependencies and Sanitized Domain Contracts

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/VehicleSnapshot.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/VehicleLoadResult.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/domain/VehicleSnapshotTest.kt`

**Interfaces:**
- Consumes: existing `DataFreshness` and `FreshnessPolicy`.
- Produces: `VehicleSnapshot`, `VehicleBatch`, `BusDataError`, and `VehicleLoadResult` used by every later task.

- [ ] **Step 1: Write the failing domain contract test**

```kotlin
package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VehicleSnapshotTest {
    @Test fun snapshotContainsOnlyDisplaySafeFields() {
        val fields = VehicleSnapshot::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("vhc", ignoreCase = true) || it.contains("vehicleNo", ignoreCase = true) })
    }

    @Test fun batchSortsByDirectionThenSequence() {
        val fetchedAt = Instant.parse("2026-07-16T12:00:00Z")
        val items = listOf(
            VehicleSnapshot("3000814001", "814", "1", "B", 8, 35.8, 128.6, "soon", null, null),
            VehicleSnapshot("3000814001", "814", "0", "A", 4, 35.7, 128.5, "soon", null, null),
        )
        assertEquals(listOf("A", "B"), VehicleBatch.from(items, fetchedAt).vehicles.map { it.stopId })
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails because the contracts do not exist**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*VehicleSnapshotTest' --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx384m -Xss512k -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```
Expected: compilation failure naming `VehicleSnapshot` and `VehicleBatch`.

- [ ] **Step 3: Add exact dependency aliases**

Add to `gradle/libs.versions.toml`:
```toml
lifecycle = "2.11.0"
coroutines = "1.11.0"

lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

Add to `app/build.gradle.kts`:
```kotlin
implementation(libs.lifecycle.viewmodel)
implementation(libs.coroutines.android)
implementation(libs.okhttp)
implementation(libs.gson)
testImplementation(libs.coroutines.test)
testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 4: Implement the sanitized domain contracts**

Create `VehicleSnapshot.kt`:
```kotlin
package com.rafaam11.businfo.domain

import java.time.Instant

data class VehicleSnapshot(
    val routeId: String,
    val routeNo: String,
    val moveDirection: String,
    val stopId: String?,
    val stopSequence: Int?,
    val latitude: Double,
    val longitude: Double,
    val arrivalState: String?,
    val busTypeCode2: String?,
    val busTypeCode3: String?,
)

data class VehicleBatch private constructor(val vehicles: List<VehicleSnapshot>, val fetchedAt: Instant) {
    companion object {
        fun from(unsorted: Collection<VehicleSnapshot>, fetchedAt: Instant) = VehicleBatch(
            unsorted.sortedWith(compareBy<VehicleSnapshot>({ it.moveDirection }, { it.stopSequence ?: Int.MAX_VALUE })),
            fetchedAt,
        )
    }
}
```

Create `VehicleLoadResult.kt`:
```kotlin
package com.rafaam11.businfo.domain

sealed interface BusDataError {
    data object InvalidCredential : BusDataError
    data object RateLimited : BusDataError
    data object NetworkUnavailable : BusDataError
    data object ServiceUnavailable : BusDataError
    data object MalformedResponse : BusDataError
}

sealed interface VehicleLoadResult {
    data class Success(val batch: VehicleBatch) : VehicleLoadResult
    data class Failure(val error: BusDataError, val retained: VehicleBatch?) : VehicleLoadResult
}
```

- [ ] **Step 5: Run the focused and existing domain tests**

Run the Task 1 command without `--tests`, and expect all app unit tests to pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/rafaam11/businfo/domain app/src/test/kotlin/com/rafaam11/businfo/domain
git commit -m "feat: add live vehicle domain contracts"
```

### Task 2: Parse and Validate the Verified Daegu API Contract

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/remote/DaeguBusRemoteDataSource.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSource.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/data/remote/OkHttpDaeguBusRemoteDataSourceTest.kt`

**Interfaces:**
- Consumes: `VehicleSnapshot` and `BusDataError` from Task 1.
- Produces: `suspend fun validateKey(serviceKey: String): RemoteResult<Unit>` and `suspend fun vehicles(serviceKey: String, routeId: String): RemoteResult<List<VehicleSnapshot>>`.

- [ ] **Step 1: Write MockWebServer tests for success, header failure, empty items, and sensitive-field exclusion**

Use fixtures embedded as string constants containing the verified envelope shape. Assert the recorded validation path is `/getBasic02`, vehicle query contains `routeId=3000814001`, `xPos=128.6` becomes longitude, `yPos=35.8` becomes latitude, empty `items` returns an empty list, and result code `9003` returns `InvalidCredential`. Include a fake `vhcNo2` in JSON and assert it cannot be found through reflection on the returned model.

```kotlin
@Test fun http200WithFailureHeaderIsNotSuccess() = runTest {
    server.enqueue(MockResponse().setBody("""{"header":{"resultCode":"9003","resultMsg":"error","success":false},"body":{"totalCount":0,"items":[]}}"""))
    assertEquals(RemoteResult.Failure(BusDataError.InvalidCredential), source.validateKey("secret"))
}
```

- [ ] **Step 2: Run the focused test and confirm missing remote types fail compilation**

Run the low-memory command from Task 1 with `--tests '*OkHttpDaeguBusRemoteDataSourceTest'`.

- [ ] **Step 3: Implement the remote contract**

```kotlin
package com.rafaam11.businfo.data.remote

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleSnapshot

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: BusDataError) : RemoteResult<Nothing>
}

interface DaeguBusRemoteDataSource {
    suspend fun validateKey(serviceKey: String): RemoteResult<Unit>
    suspend fun vehicles(serviceKey: String, routeId: String): RemoteResult<List<VehicleSnapshot>>
}
```

- [ ] **Step 4: Implement the OkHttp/Gson adapter with one private request path**

`OkHttpDaeguBusRemoteDataSource` must accept `OkHttpClient`, `HttpUrl`, and `Clock` in its constructor. Its private `request(endpoint, serviceKey, parameters)` must build the URL with `HttpUrl.Builder.addQueryParameter`, execute on `Dispatchers.IO`, reject non-2xx responses, parse `header.resultCode/resultMsg/success`, and never construct a log string from the URL or body. Parse `body.items` as an array; map only the approved fields and discard rows without numeric `xPos/yPos` or a nonblank `routeId`.

```kotlin
private fun classify(code: String, message: String): BusDataError = when {
    code == "9003" -> BusDataError.InvalidCredential
    message.contains("한도") || message.contains("초과") -> BusDataError.RateLimited
    else -> BusDataError.ServiceUnavailable
}
```

Catch `IOException` as `NetworkUnavailable`, `JsonParseException` and missing envelope members as `MalformedResponse`, and let coroutine cancellation propagate.

- [ ] **Step 5: Run remote tests and all app tests**

Expect the focused class and then `:app:testDebugUnitTest` to pass.

- [ ] **Step 6: Commit Task 2**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/data/remote app/src/test/kotlin/com/rafaam11/businfo/data/remote
git commit -m "feat: add verified Daegu API client"
```

### Task 3: Persist Credentials and Preserve the Last Successful Snapshot

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/credential/CredentialStore.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/credential/SharedPreferencesCredentialStore.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/data/BusRepository.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/data/BusRepositoryTest.kt`

**Interfaces:**
- Consumes: `DaeguBusRemoteDataSource`, `RemoteResult`, `VehicleBatch`, and `VehicleLoadResult`.
- Produces: credential lifecycle and repository refresh API used by the ViewModel.

- [ ] **Step 1: Write repository tests with fakes**

Cover blank-key rejection without a network call, save only after successful validation, successful vehicle fetch with an injected `Clock`, failure retaining the previous `VehicleBatch`, and successful empty response replacing prior vehicles with an empty batch.

```kotlin
@Test fun failedRefreshRetainsLastSuccess() = runTest {
    remote.vehicleResults.add(RemoteResult.Success(listOf(vehicle)))
    remote.vehicleResults.add(RemoteResult.Failure(BusDataError.NetworkUnavailable))
    repository.refreshVehicles()
    val second = repository.refreshVehicles() as VehicleLoadResult.Failure
    assertEquals(listOf(vehicle), second.retained?.vehicles)
}
```

- [ ] **Step 2: Run the focused test and confirm missing repository types fail compilation**

Run the low-memory test command with `--tests '*BusRepositoryTest'`.

- [ ] **Step 3: Implement credential storage contracts**

```kotlin
package com.rafaam11.businfo.data.credential

interface CredentialStore {
    fun read(): String?
    fun write(serviceKey: String)
    fun clear()
}
```

`SharedPreferencesCredentialStore(context)` uses `context.getSharedPreferences("credentials", Context.MODE_PRIVATE)`, stores under private key `daegu_service_key`, trims input, and returns null for a missing or blank value.

- [ ] **Step 4: Implement `BusRepository`**

```kotlin
class BusRepository(
    private val credentials: CredentialStore,
    private val remote: DaeguBusRemoteDataSource,
    private val clock: Clock,
) {
    private var lastSuccessful: VehicleBatch? = null
    fun savedKeyExists(): Boolean = credentials.read() != null
    fun clearKey() { credentials.clear(); lastSuccessful = null }
    suspend fun validateAndSave(key: String): BusDataError?
    suspend fun refreshVehicles(): VehicleLoadResult
}
```

`validateAndSave` returns `InvalidCredential` immediately for blank input, calls `validateKey`, and writes only after success. `refreshVehicles` returns `InvalidCredential` when no key exists, requests constant route ID `3000814001`, creates `VehicleBatch.from(result.value, clock.instant())` on success, updates `lastSuccessful`, and passes `lastSuccessful` into failures.

- [ ] **Step 5: Run repository and full app tests**

Expect all tests to pass and `git diff --check` to report no whitespace errors.

- [ ] **Step 6: Commit Task 3**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/data app/src/test/kotlin/com/rafaam11/businfo/data
git commit -m "feat: cache live vehicles and persist API key"
```

### Task 4: Serialize User Events in the ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListUiState.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListViewModel.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/ui/VehicleListViewModelTest.kt`

**Interfaces:**
- Consumes: `BusRepository`, `VehicleLoadResult`, `FreshnessPolicy`, and injected coroutine dispatcher.
- Produces: `StateFlow<VehicleListUiState>`, `submitKey`, `refresh`, and `clearKey`.

- [ ] **Step 1: Write deterministic coroutine tests**

Use `StandardTestDispatcher`, a fake repository facade, and `runTest`. Verify initial `NeedsKey`/loading behavior, successful key transition to content, invalid-key message, retained content plus nonblocking error, empty content message, and two refresh calls while loading causing only one repository call.

```kotlin
@Test fun duplicateRefreshWhileLoadingIsIgnored() = runTest {
    val viewModel = VehicleListViewModel(repository, backgroundScope, testScheduler.currentTimeClock())
    viewModel.refresh()
    viewModel.refresh()
    runCurrent()
    assertEquals(1, repository.refreshCalls)
}
```

- [ ] **Step 2: Run the focused test and confirm missing UI state types fail compilation**

Run the low-memory command with `--tests '*VehicleListViewModelTest'`.

- [ ] **Step 3: Define immutable UI state**

```kotlin
sealed interface VehicleListUiState {
    data object Starting : VehicleListUiState
    data class NeedsKey(val submitting: Boolean = false, val error: BusDataError? = null) : VehicleListUiState
    data class Content(
        val batch: VehicleBatch?,
        val refreshing: Boolean,
        val error: BusDataError? = null,
    ) : VehicleListUiState
}
```

- [ ] **Step 4: Implement ViewModel event guards and transitions**

The ViewModel initializes from `repository.savedKeyExists()`. `submitKey` ignores calls while submitting, switches to `NeedsKey(submitting=true)`, validates, and on success calls one refresh. `refresh` ignores calls when the current `Content.refreshing` is true. A failure with retained data stays in `Content`; a failure without retained data stays in `Content(batch=null, error=...)`. `clearKey` cancels no independent jobs because only the current event job exists, clears the repository, and returns to `NeedsKey`.

- [ ] **Step 5: Run ViewModel tests and the whole app unit suite**

Expect all tests to pass.

- [ ] **Step 6: Commit Task 4**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/ui app/src/test/kotlin/com/rafaam11/businfo/ui
git commit -m "feat: coordinate vehicle list screen state"
```

### Task 5: Replace the Placeholder with the Installable Compose Experience

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/AppGraph.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/ui/VehicleListScreen.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt`
- Test: `app/src/test/kotlin/com/rafaam11/businfo/ui/VehicleListPresentationTest.kt`

**Interfaces:**
- Consumes: Task 4 state and events.
- Produces: visible key onboarding and route 814 list in the installed app.

- [ ] **Step 1: Write pure presentation mapping tests**

Extract `fun BusDataError.userMessage(): String` and `fun VehicleSnapshot.primaryText(): String`. Assert exact Korean messages for invalid credential, network, service, malformed, and rate-limit states; assert vehicle text contains direction/sequence but not any vehicle identifier.

```kotlin
@Test fun networkFailureHasRetryableCopy() {
    assertEquals("네트워크에 연결할 수 없습니다. 마지막 정상 데이터는 유지됩니다.", BusDataError.NetworkUnavailable.userMessage())
}
```

- [ ] **Step 2: Run the focused presentation test and confirm it fails**

Run the low-memory command with `--tests '*VehicleListPresentationTest'`.

- [ ] **Step 3: Assemble production dependencies in `AppGraph`**

```kotlin
class AppGraph(context: Context) {
    private val credentials = SharedPreferencesCredentialStore(context.applicationContext)
    private val remote = OkHttpDaeguBusRemoteDataSource(
        client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
        baseUrl = "https://apis.data.go.kr/6270000/dbmsapi02/".toHttpUrl(),
        clock = Clock.systemUTC(),
    )
    val repository = BusRepository(credentials, remote, Clock.systemUTC())
}
```

- [ ] **Step 4: Implement the Compose states**

`VehicleListScreen` uses `Scaffold`, `TopAppBar`, `OutlinedTextField` with `PasswordVisualTransformation`, `Button`, `CircularProgressIndicator`, `LazyColumn`, and Material 3 cards. Content renders `814번 실시간 차량`, vehicle count, a Korean freshness label from `FreshnessPolicy`, last fetch elapsed seconds, `새로고침`, and `API 키 변경`. A one-second `LaunchedEffect` ticker updates only elapsed-time display and performs no network request. An empty successful batch renders `현재 운행 차량 없음`.

- [ ] **Step 5: Wire Activity, ViewModel, and app root**

`MainActivity` creates one `AppGraph`, obtains `VehicleListViewModel` from `ViewModelProvider` with an explicit `ViewModelProvider.Factory`, and calls `setContent { BusInfoApp(viewModel) }`. `BusInfoApp` collects the ViewModel state with `collectAsState`, applies `MaterialTheme`, and delegates events to `VehicleListScreen`. Remove the placeholder text completely.

- [ ] **Step 6: Run presentation tests and compile the debug app**

Run all unit tests, then:
```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Xss512k -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```
Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit Task 5**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo app/src/test/kotlin/com/rafaam11/businfo/ui
git commit -m "feat: show route 814 live vehicles"
```

### Task 6: Regression, Privacy, and Real-Device Handoff

**Files:**
- Modify: `README.md`
- Modify: `docs/api-contract-runbook.md`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: complete vertical slice.
- Produces: reproducible APK and exact real-device acceptance checklist.

- [ ] **Step 1: Run both module test suites separately**

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx384m -Xss512k -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat :api-probe:test --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx384m -Xss512k -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```
Expected: both commands report `BUILD SUCCESSFUL` with zero failures.

- [ ] **Step 2: Scan tracked sources and APK inputs for secrets**

```powershell
git grep -n -I -E 'serviceKey\s*=|vhcNo2|DAEGU_SERVICE_KEY' -- ':!docs/api-contract-report.md' ':!api-probe/src/test/**'
git status --short
```
Expected: no literal service key or production `vhcNo2` persistence/display path; only intentional symbol names in safe probe/redaction or test assertions are reviewed individually.

- [ ] **Step 3: Document the user-visible test flow**

Add README/runbook instructions: install the debug APK, enter the Decoding service key, expect the 814 list during service hours, tap refresh, disable networking and verify the retained-list banner, enter a wrong key and verify credential error, then restore the valid key. State that this slice has no map/widget yet.

- [ ] **Step 4: Rebuild the final APK and record its hash**

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Xss512k -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
Get-FileHash 'app\build\outputs\apk\debug\app-debug.apk' -Algorithm SHA256
```
Expected: successful build and a SHA-256 value suitable for handoff comparison.

- [ ] **Step 5: Commit documentation and verify a clean branch**

```powershell
git add README.md docs/api-contract-runbook.md
git commit -m "docs: add live vehicle APK test flow"
git status --short
git log -6 --oneline
```
Expected: empty status and six focused implementation commits after the plan commit.

## Plan Self-Review

- Spec coverage: API-key entry, live 814 list, manual refresh, retained last success, empty-result semantics, freshness, error categories, privacy, tests, and APK handoff all map to Tasks 1–6.
- Scope boundary: Room, commute cards, NAVER Map, lifecycle polling, stale markers, and Glance are intentionally excluded by the approved 2026-07-16 slice spec and require their own approved specs/plans.
- Type consistency: `VehicleSnapshot`, `VehicleBatch`, `BusDataError`, `RemoteResult`, `VehicleLoadResult`, `BusRepository`, and `VehicleListUiState` retain the same names and direction across tasks.
- Placeholder scan: the plan contains no deferred implementation markers; every excluded feature is named as a separate follow-up scope rather than an incomplete step.
