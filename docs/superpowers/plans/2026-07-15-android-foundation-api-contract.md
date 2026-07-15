# Android Foundation and API Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a buildable Android foundation plus a repeatable, secret-safe probe that proves the real Daegu bus API request and response contract before app DTOs, maps, or widgets are implemented.

**Architecture:** A single Android `app` module establishes the production package and Compose baseline. A separate JVM `api-probe` module calls only the five approved public-data endpoints, stores raw responses under ignored local storage, and emits a sanitized schema report. The next implementation plan must use that observed report instead of guessed DTO fields.

**Tech Stack:** Android Gradle Plugin 9.3.0, Gradle 9.5.0, JDK 17, built-in Kotlin, Kotlin 2.4.10 Compose plugin, Compose BOM 2026.06.00, JUnit 4.13.2, OkHttp 4.12.0, Gson 2.13.2.

## Global Constraints

- Package name and application ID: `com.rafaam11.businfo`.
- `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`.
- App name: `대구 버스`.
- Do not request Android location permission.
- Do not commit the public-data service key, NAVER Maps key ID, signing keys, raw API responses, `local.properties`, or `.superpowers/`.
- Use only stable dependency releases; do not introduce alpha or beta artifacts.
- The API probe may call only `getBasic02`, `getBs02`, `getLink02`, `getRealtime02`, and `getPos02` under `https://apis.data.go.kr/6270000/dbmsapi02/`.
- Do not define production DTOs until the live contract report exists.
- This plan ends at the API contract gate. Dashboard, map, Room, and Glance implementation belong in the evidence-based follow-up plan.

## File Map

```text
settings.gradle.kts                         Plugin repositories and :app/:api-probe modules
build.gradle.kts                            Root plugin versions
gradle/libs.versions.toml                   Stable dependency coordinates
gradle/wrapper/gradle-wrapper.properties    Gradle 9.5.0 wrapper
gradle.properties                           AndroidX and Gradle defaults
app/build.gradle.kts                        Android/Compose application build
app/src/main/AndroidManifest.xml             Package entry point, no location permission
app/src/main/kotlin/com/rafaam11/businfo/
  MainActivity.kt                           Compose host activity
  BusInfoApp.kt                             Foundation screen
app/src/test/kotlin/com/rafaam11/businfo/
  FoundationContractTest.kt                 Package and policy smoke tests
app/src/main/kotlin/com/rafaam11/businfo/domain/
  DataFreshness.kt                          Freshness states
  FreshnessPolicy.kt                        15-second/30-second classification
  PollingPolicy.kt                          8/15/30-second request policy
app/src/test/kotlin/com/rafaam11/businfo/domain/
  FreshnessPolicyTest.kt                    Boundary tests
  PollingPolicyTest.kt                      Backoff and stop tests
api-probe/build.gradle.kts                  JVM probe module
api-probe/src/main/kotlin/com/rafaam11/businfo/probe/
  Main.kt                                   CLI entry point
  ProbeCommand.kt                           Argument and endpoint validation
  SecretLoader.kt                           Ignored local.properties key loading
  DaeguApiProbe.kt                          Allowlisted HTTP client
  JsonShapeReporter.kt                      Sanitized JSON path/type/sample report
  ProbeReportWriter.kt                      Local raw and Markdown report output
api-probe/src/test/kotlin/com/rafaam11/businfo/probe/
  ProbeCommandTest.kt                       CLI validation tests
  DaeguApiProbeTest.kt                      URL and redaction tests
  JsonShapeReporterTest.kt                  Schema report tests
docs/api-contract-runbook.md                Repeatable live-probe procedure
docs/api-contract-report.md                 Generated evidence, added only after live run
```

---

### Task 1: Buildable Android and JVM Foundation

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create via wrapper command: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/MainActivity.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/BusInfoApp.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/FoundationContractTest.kt`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: approved package, SDK floors, app name, and stable version constraints from the design.
- Produces: `:app` Android module, `:api-probe` module slot, and `BusInfoApp()` Compose entry point.

- [ ] **Step 1: Write the failing foundation test**

```kotlin
package com.rafaam11.businfo

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationContractTest {
    @Test
    fun productionPackageIsStable() {
        assertEquals("com.rafaam11.businfo", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run the test before scaffolding and verify the expected failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*.FoundationContractTest"`

Expected: FAIL because the Gradle wrapper and `:app` module do not exist.

- [ ] **Step 3: Create the root build configuration**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://repository.map.naver.com/archive/maven")
    }
}
rootProject.name = "bus-info"
include(":app")
include(":api-probe")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.3.0"
kotlin = "2.4.10"
composeBom = "2026.06.00"
activityCompose = "1.12.4"
junit = "4.13.2"
okhttp = "4.12.0"
gson = "2.13.2"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
junit = { module = "junit:junit", version.ref = "junit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 4: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 9.5.0 --distribution-type bin`

Expected: `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties` exist, and the properties file points to `gradle-9.5.0-bin.zip`.

- [ ] **Step 5: Create the Android app module**

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rafaam11.businfo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rafaam11.businfo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    androidTestImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
}
```

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="대구 버스"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`MainActivity.kt`:

```kotlin
package com.rafaam11.businfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BusInfoApp() }
    }
}
```

`BusInfoApp.kt`:

```kotlin
package com.rafaam11.businfo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BusInfoApp() {
    MaterialTheme {
        Surface { Text("대구 버스 API 연결 준비") }
    }
}
```

- [ ] **Step 6: Create the probe module shell and strengthen ignore rules**

`api-probe/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application {
    mainClass = "com.rafaam11.businfo.probe.MainKt"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
```

Append to `.gitignore`:

```gitignore
# Local API contract evidence and secrets
.local/
```

- [ ] **Step 7: Run the foundation verification**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :api-probe:test`

Expected: BUILD SUCCESSFUL; `FoundationContractTest` passes; the debug APK is created.

- [ ] **Step 8: Commit the foundation**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app api-probe/build.gradle.kts
git commit -m "build: scaffold Android bus app foundation"
```

---

### Task 2: Freshness and Polling Domain Policies

**Files:**
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/DataFreshness.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/FreshnessPolicy.kt`
- Create: `app/src/main/kotlin/com/rafaam11/businfo/domain/PollingPolicy.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/domain/FreshnessPolicyTest.kt`
- Create: `app/src/test/kotlin/com/rafaam11/businfo/domain/PollingPolicyTest.kt`

**Interfaces:**
- Consumes: exact 15-second delayed boundary, 30-second stale boundary, and 8/15/30-second retry policy.
- Produces: `FreshnessPolicy.classify(Instant, Instant): DataFreshness` and `PollingPolicy.after(PollResult): PollDecision` for later Repository and map work.

- [ ] **Step 1: Write freshness boundary tests**

```kotlin
package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessPolicyTest {
    private val now = Instant.parse("2026-07-15T10:00:00Z")

    @Test fun fifteenSecondsIsFresh() = assertEquals(
        DataFreshness.FRESH,
        FreshnessPolicy.classify(now.minusSeconds(15), now),
    )

    @Test fun sixteenSecondsIsDelayed() = assertEquals(
        DataFreshness.DELAYED,
        FreshnessPolicy.classify(now.minusSeconds(16), now),
    )

    @Test fun thirtySecondsIsDelayed() = assertEquals(
        DataFreshness.DELAYED,
        FreshnessPolicy.classify(now.minusSeconds(30), now),
    )

    @Test fun thirtyOneSecondsIsStale() = assertEquals(
        DataFreshness.STALE,
        FreshnessPolicy.classify(now.minusSeconds(31), now),
    )

    @Test fun futureClockSkewIsFresh() = assertEquals(
        DataFreshness.FRESH,
        FreshnessPolicy.classify(now.plusSeconds(2), now),
    )
}
```

- [ ] **Step 2: Run freshness tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*.FreshnessPolicyTest"`

Expected: FAIL with unresolved `DataFreshness` and `FreshnessPolicy`.

- [ ] **Step 3: Implement the freshness policy**

```kotlin
package com.rafaam11.businfo.domain

enum class DataFreshness { FRESH, DELAYED, STALE, UNAVAILABLE }
```

```kotlin
package com.rafaam11.businfo.domain

import java.time.Duration
import java.time.Instant

object FreshnessPolicy {
    fun classify(observedAt: Instant?, now: Instant): DataFreshness {
        if (observedAt == null) return DataFreshness.UNAVAILABLE
        val ageSeconds = Duration.between(observedAt, now).seconds.coerceAtLeast(0)
        return when {
            ageSeconds <= 15 -> DataFreshness.FRESH
            ageSeconds <= 30 -> DataFreshness.DELAYED
            else -> DataFreshness.STALE
        }
    }
}
```

- [ ] **Step 4: Run freshness tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*.FreshnessPolicyTest"`

Expected: five tests PASS.

- [ ] **Step 5: Write polling policy tests**

```kotlin
package com.rafaam11.businfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PollingPolicyTest {
    @Test fun successUsesEightSeconds() = assertEquals(PollDecision.Wait(8), PollingPolicy.after(PollResult.Success))
    @Test fun firstFailureUsesFifteenSeconds() = assertEquals(PollDecision.Wait(15), PollingPolicy.after(PollResult.TransientFailure(1)))
    @Test fun laterFailureUsesThirtySeconds() = assertEquals(PollDecision.Wait(30), PollingPolicy.after(PollResult.TransientFailure(2)))
    @Test fun authenticationStops() = assertEquals(PollDecision.Stop, PollingPolicy.after(PollResult.AuthenticationFailure))
    @Test fun quotaStops() = assertEquals(PollDecision.Stop, PollingPolicy.after(PollResult.QuotaExceeded))
}
```

- [ ] **Step 6: Run polling tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*.PollingPolicyTest"`

Expected: FAIL with unresolved polling policy types.

- [ ] **Step 7: Implement the polling policy and rerun all domain tests**

```kotlin
package com.rafaam11.businfo.domain

sealed interface PollResult {
    data object Success : PollResult
    data class TransientFailure(val consecutiveCount: Int) : PollResult
    data object AuthenticationFailure : PollResult
    data object QuotaExceeded : PollResult
}

sealed interface PollDecision {
    data class Wait(val seconds: Long) : PollDecision
    data object Stop : PollDecision
}

object PollingPolicy {
    fun after(result: PollResult): PollDecision = when (result) {
        PollResult.Success -> PollDecision.Wait(8)
        is PollResult.TransientFailure -> PollDecision.Wait(if (result.consecutiveCount == 1) 15 else 30)
        PollResult.AuthenticationFailure, PollResult.QuotaExceeded -> PollDecision.Stop
    }
}
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*.domain.*"`

Expected: ten tests PASS.

- [ ] **Step 8: Commit the domain policies**

```powershell
git add app/src/main/kotlin/com/rafaam11/businfo/domain app/src/test/kotlin/com/rafaam11/businfo/domain
git commit -m "feat: define freshness and polling policies"
```

---

### Task 3: Secret-Safe Generic Daegu API Probe

**Files:**
- Create: `api-probe/src/main/kotlin/com/rafaam11/businfo/probe/ProbeCommand.kt`
- Create: `api-probe/src/main/kotlin/com/rafaam11/businfo/probe/SecretLoader.kt`
- Create: `api-probe/src/main/kotlin/com/rafaam11/businfo/probe/DaeguApiProbe.kt`
- Create: `api-probe/src/main/kotlin/com/rafaam11/businfo/probe/JsonShapeReporter.kt`
- Create: `api-probe/src/main/kotlin/com/rafaam11/businfo/probe/ProbeReportWriter.kt`
- Create: `api-probe/src/main/kotlin/com/rafaam11/businfo/probe/Main.kt`
- Create: `api-probe/src/test/kotlin/com/rafaam11/businfo/probe/ProbeCommandTest.kt`
- Create: `api-probe/src/test/kotlin/com/rafaam11/businfo/probe/DaeguApiProbeTest.kt`
- Create: `api-probe/src/test/kotlin/com/rafaam11/businfo/probe/JsonShapeReporterTest.kt`

**Interfaces:**
- Consumes: endpoint name plus zero or more runtime `--param name=value` pairs and `DAEGU_BUS_SERVICE_KEY` from ignored `local.properties`.
- Produces: `.local/api-probe/raw/<endpoint>.json`, `.local/api-probe/reports/<endpoint>.md`, and sanitized console output; never returns or prints the service key.

- [ ] **Step 1: Write CLI validation tests**

```kotlin
package com.rafaam11.businfo.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeCommandTest {
    @Test fun parsesAllowlistedEndpointAndParameters() {
        val command = ProbeCommand.parse(arrayOf("getPos02", "--param", "routeId=123"))
        assertEquals("getPos02", command.endpoint)
        assertEquals(mapOf("routeId" to "123"), command.parameters)
    }

    @Test fun rejectsUnknownEndpoint() {
        assertThrows(IllegalArgumentException::class.java) { ProbeCommand.parse(arrayOf("anythingElse")) }
    }
}
```

- [ ] **Step 2: Run CLI tests and verify they fail**

Run: `./gradlew.bat :api-probe:test --tests "*.ProbeCommandTest"`

Expected: FAIL with unresolved `ProbeCommand`.

- [ ] **Step 3: Implement strict command parsing**

```kotlin
package com.rafaam11.businfo.probe

data class ProbeCommand(val endpoint: String, val parameters: Map<String, String>) {
    companion object {
        private val allowed = setOf("getBasic02", "getBs02", "getLink02", "getRealtime02", "getPos02")

        fun parse(args: Array<String>): ProbeCommand {
            require(args.isNotEmpty()) { "Endpoint is required" }
            require(args.first() in allowed) { "Endpoint is not allowlisted" }
            val values = linkedMapOf<String, String>()
            var index = 1
            while (index < args.size) {
                require(args[index] == "--param" && index + 1 < args.size) { "Use --param name=value" }
                val pair = args[index + 1].split('=', limit = 2)
                require(pair.size == 2 && pair[0].isNotBlank()) { "Parameter must be name=value" }
                require(pair[0] != "serviceKey") { "Service key must come from ignored local.properties" }
                values[pair[0]] = pair[1]
                index += 2
            }
            return ProbeCommand(args.first(), values)
        }
    }
}
```

- [ ] **Step 4: Write HTTP and report tests**

```kotlin
package com.rafaam11.businfo.probe

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaeguApiProbeTest {
    @Test fun serviceKeyIsSentButNeverRendered() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"items\":[]}"))
            server.start()
            val result = DaeguApiProbe(server.url("/"), "secret-value").execute(ProbeCommand("getPos02", mapOf("routeId" to "123")))
            assertTrue(server.takeRequest().requestUrl!!.queryParameter("serviceKey") == "secret-value")
            assertFalse(result.requestSummary.contains("secret-value"))
        }
    }
}
```

```kotlin
package com.rafaam11.businfo.probe

import com.google.gson.JsonParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonShapeReporterTest {
    @Test fun reportsPathsAndMasksVehicleNumbers() {
        val json = JsonParser.parseString("""{"items":[{"lat":35.8,"vehicleNo":"1234"}]}""")
        val report = JsonShapeReporter.render(json)
        assertTrue(report.contains("$.items[].lat | number | 35.8"))
        assertTrue(report.contains("$.items[].vehicleNo | string | [redacted]"))
        assertFalse(report.contains("1234"))
    }
}
```

- [ ] **Step 5: Run HTTP and report tests and verify they fail**

Run: `./gradlew.bat :api-probe:test --tests "*.DaeguApiProbeTest" --tests "*.JsonShapeReporterTest"`

Expected: FAIL with unresolved probe and reporter types.

- [ ] **Step 6: Implement secret loading and the HTTP client**

```kotlin
package com.rafaam11.businfo.probe

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object SecretLoader {
    fun projectDir(start: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath()): Path {
        var current: Path? = start
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) return current
            current = current.parent
        }
        error("Project root containing settings.gradle.kts was not found")
    }

    fun daeguServiceKey(projectDir: Path = projectDir()): String {
        val file = projectDir.resolve("local.properties")
        require(Files.exists(file)) { "Create ignored local.properties with DAEGU_BUS_SERVICE_KEY" }
        val properties = Properties().apply { Files.newInputStream(file).use { load(it) } }
        return requireNotNull(properties.getProperty("DAEGU_BUS_SERVICE_KEY")) { "DAEGU_BUS_SERVICE_KEY is missing" }.trim()
    }
}
```

```kotlin
package com.rafaam11.businfo.probe

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProbeResponse(val statusCode: Int, val body: String, val requestSummary: String)

class DaeguApiProbe(
    private val baseUrl: HttpUrl = "https://apis.data.go.kr/6270000/dbmsapi02/".toHttpUrl(),
    private val serviceKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun execute(command: ProbeCommand): ProbeResponse {
        val url = baseUrl.newBuilder()
            .addPathSegment(command.endpoint)
            .addQueryParameter("serviceKey", serviceKey)
            .apply { command.parameters.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            return ProbeResponse(
                statusCode = response.code,
                body = requireNotNull(response.body).string(),
                requestSummary = url.newBuilder().removeAllQueryParameters("serviceKey").addQueryParameter("serviceKey", "[redacted]").build().toString(),
            )
        }
    }
}
```

- [ ] **Step 7: Implement the recursive shape reporter**

```kotlin
package com.rafaam11.businfo.probe

import com.google.gson.JsonElement

object JsonShapeReporter {
    private val sensitive = Regex("(?i)(service.?key|token|secret|vehicle.?no|plate)")

    fun render(root: JsonElement): String {
        val fields = sortedMapOf<String, String>()
        visit("$", root, fields)
        return fields.entries.joinToString("\n") { (path, value) -> "$path | $value" }
    }

    private fun visit(path: String, node: JsonElement, fields: MutableMap<String, String>) {
        when {
            node.isJsonObject -> node.asJsonObject.entrySet().forEach { (name, value) -> visit("$path.$name", value, fields) }
            node.isJsonArray -> node.asJsonArray.forEach { visit("$path[]", it, fields) }
            node.isJsonNull -> fields.putIfAbsent(path, "null | null")
            node.asJsonPrimitive.isBoolean -> fields.putIfAbsent(path, "boolean | ${node.asBoolean}")
            node.asJsonPrimitive.isNumber -> fields.putIfAbsent(path, "number | ${node.asNumber}")
            else -> {
                val sample = if (sensitive.containsMatchIn(path.substringAfterLast('.'))) "[redacted]" else node.asString.take(80)
                fields.putIfAbsent(path, "string | $sample")
            }
        }
    }
}
```

- [ ] **Step 8: Implement local output and the CLI entry point**

```kotlin
package com.rafaam11.businfo.probe

import java.nio.file.Files
import java.nio.file.Path

object ProbeReportWriter {
    fun write(command: ProbeCommand, response: ProbeResponse, shape: String, projectDir: Path): Path {
        val root = projectDir.resolve(".local/api-probe")
        val raw = root.resolve("raw/${command.endpoint}.json")
        val report = root.resolve("reports/${command.endpoint}.md")
        Files.createDirectories(raw.parent)
        Files.createDirectories(report.parent)
        Files.writeString(raw, response.body)
        Files.writeString(report, "# ${command.endpoint}\n\n- HTTP: ${response.statusCode}\n- Request: `${response.requestSummary}`\n\n```text\n$shape\n```\n")
        return report
    }
}
```

```kotlin
package com.rafaam11.businfo.probe

import com.google.gson.JsonParser
fun main(args: Array<String>) {
    val command = ProbeCommand.parse(args)
    val projectDir = SecretLoader.projectDir()
    val response = DaeguApiProbe(serviceKey = SecretLoader.daeguServiceKey(projectDir)).execute(command)
    require(response.statusCode in 200..299) { "HTTP ${response.statusCode}" }
    val shape = JsonShapeReporter.render(JsonParser.parseString(response.body))
    val report = ProbeReportWriter.write(command, response, shape, projectDir)
    println("Wrote sanitized report: $report")
}
```

- [ ] **Step 9: Run the complete probe test suite**

Run: `./gradlew.bat :api-probe:test`

Expected: four tests PASS and no test output contains `secret-value` or `1234`.

- [ ] **Step 10: Commit the generic probe**

```powershell
git add api-probe/src
git commit -m "feat: add secret-safe Daegu API contract probe"
```

---

### Task 4: Runbook and Live Contract Gate

**Files:**
- Create: `docs/api-contract-runbook.md`
- Generate locally: `.local/api-probe/raw/*.json`
- Generate locally: `.local/api-probe/reports/*.md`
- Create after successful probe: `docs/api-contract-report.md`
- Modify after evidence exists: `docs/superpowers/specs/2026-07-15-daegu-bus-android-app-design.md`

**Interfaces:**
- Consumes: activated public-data key, exact Swagger parameter names visible after login, and the Task 3 probe.
- Produces: a committed sanitized contract report that classifies `getPos02` as `GPS_COORDINATES`, `ROUTE_SEGMENT_ONLY`, or `INSUFFICIENT_DATA`, and lists exact request/response field names for the follow-up plan.

- [ ] **Step 1: Write the runbook with the exact secret boundary and commands**

`docs/api-contract-runbook.md`:

```markdown
# 대구 버스 API 계약 확인 Runbook

1. 공공데이터포털에서 `대구광역시_대구버스정보시스템` 활용 신청이 `승인` 상태인지 확인한다.
2. 저장소 루트의 ignored `local.properties`에 `DAEGU_BUS_SERVICE_KEY=<일반 인증키 Decoding 값>`을 추가한다.
3. `./gradlew.bat :api-probe:run --args="getBasic02"`를 실행한다.
4. `.local/api-probe/reports/getBasic02.md`에서 실제 노선 ID와 정류장 ID 필드 이름을 찾고, raw 파일에서 현재 운행할 가능성이 높은 노선 ID 하나를 고른다.
5. 로그인된 공공데이터포털 Swagger에서 각 endpoint의 필수 파라미터 이름을 그대로 확인한다.
6. 아래 형식으로 나머지 endpoint를 실행한다. `name`은 Swagger의 실제 이름이고 `value`는 기초 응답에서 고른 ID다.

   `./gradlew.bat :api-probe:run --args="getPos02 --param name=value"`

   `./gradlew.bat :api-probe:run --args="getBs02 --param name=value"`

   `./gradlew.bat :api-probe:run --args="getLink02 --param name=value"`

   `./gradlew.bat :api-probe:run --args="getRealtime02 --param name=value"`

7. 모든 Markdown report에서 HTTP 2xx와 JSON field paths를 확인한다. 운행 종료로 빈 응답이면 운행 시간에 다시 실행한다.
8. `getPos02`에서 위도·경도 쌍이 실제 숫자로 존재하면 `GPS_COORDINATES`, 링크·정류장 순서만 존재하면 `ROUTE_SEGMENT_ONLY`, 차량 항목 자체가 없으면 `INSUFFICIENT_DATA`로 판정한다.
9. raw 파일은 커밋하지 않는다. sanitized report에 실제 비밀값이나 차량 번호가 없는지 `rg -n "DAEGU_BUS_SERVICE_KEY=|serviceKey=[A-Za-z0-9%+/]|vehicleNo.*[0-9]{4}|plate.*[0-9]{4}" .local/api-probe/reports`로 검사한다.
10. 다섯 report를 `docs/api-contract-report.md` 하나로 합치고 exact endpoint parameter names, response paths, sample coordinate ranges, 위치 정밀도 판정을 기록한다.
```

- [ ] **Step 2: Verify the runbook is present before the live key exists**

Run: `Test-Path docs/api-contract-runbook.md`

Expected: `True`.

- [ ] **Step 3: Commit the runbook before using credentials**

```powershell
git add docs/api-contract-runbook.md
git commit -m "docs: add Daegu API contract runbook"
```

- [ ] **Step 4: Execute the live probe gate**

Run the five commands from the runbook during bus operating hours.

Expected: five HTTP 2xx report files exist under `.local/api-probe/reports/`; `getPos02.md` contains at least one vehicle record or is explicitly classified `INSUFFICIENT_DATA` after two operating-hour attempts.

- [ ] **Step 5: Create the evidence report from generated output**

Copy only sanitized field paths, parameter names, numeric coordinate ranges, response status behavior, and the three-way precision classification into `docs/api-contract-report.md`. Do not copy raw JSON wholesale.

Required headings:

```markdown
# 대구 버스 API 계약 검증 결과

## 실행 조건
## Endpoint별 필수 요청 파라미터
## Endpoint별 응답 필드
## getPos02 위치 정밀도 판정
## 오류 및 빈 응답 동작
## 후속 DTO 설계 입력값
```

- [ ] **Step 6: Verify the evidence is safe and complete**

Run:

```powershell
rg -n "^## (실행 조건|Endpoint별 필수 요청 파라미터|Endpoint별 응답 필드|getPos02 위치 정밀도 판정|오류 및 빈 응답 동작|후속 DTO 설계 입력값)$" docs/api-contract-report.md
rg -n "DAEGU_BUS_SERVICE_KEY=|serviceKey=[A-Za-z0-9%+/]|vehicleNo.*[0-9]{4}|plate.*[0-9]{4}" docs/api-contract-report.md
git check-ignore -v .local/api-probe/raw/getPos02.json local.properties
git diff --check
```

Expected: all six headings are found; the sensitive-pattern scan has no matches; both local paths are ignored; `git diff --check` is clean.

- [ ] **Step 7: Update the design with the observed classification and commit the gate**

In the design's “위치 정밀도 제한” section, replace the conditional branch with the single observed classification and exact fields documented in `docs/api-contract-report.md`. Then run:

```powershell
git add docs/api-contract-report.md docs/superpowers/specs/2026-07-15-daegu-bus-android-app-design.md
git commit -m "docs: record verified Daegu bus API contract"
git status --short --branch
```

Expected: commit succeeds and the working tree is clean. The evidence report is now the required input for the dashboard, map, Room, and Glance implementation plan.

---

## Final Verification

Run:

```powershell
./gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug :api-probe:test
git diff HEAD --check
git status --short --branch
```

Expected:

- Gradle reports `BUILD SUCCESSFUL`.
- All foundation, freshness, polling, CLI, HTTP redaction, and shape-report tests pass.
- A debug APK exists under `app/build/outputs/apk/debug/`.
- `docs/api-contract-report.md` contains the observed contract and precision classification.
- Git reports no tracked changes.

## Follow-up Planning Gate

Only after Task 4 passes, write `docs/superpowers/plans/2026-07-15-daegu-bus-app-mvp.md`. That follow-up plan must define DTO property names from `docs/api-contract-report.md`, then plan Room caching, API-key settings UI, commute cards, NAVER route map, lifecycle-bound polling, stale markers, and the manual-refresh Glance widget.
