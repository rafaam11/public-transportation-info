# Final whole-branch review fixes

Date: 2026-07-17
Branch: `feature/visual-identity-widget`

## Scope and root causes

1. Vehicle marker rotation inherited the SDK anchor instead of explicitly using the confirmed API coordinate as the visual center.
2. Widget status formatting read wall-clock time inside the Glance formatter and replaced failure age with a category label.
3. Widget-local refresh errors had no ordering relationship with newer Room snapshots written by foreground refreshes.
4. Exported `MainActivity` trusted a boolean extra and the resulting UI path called `clearKey()` before validating a replacement.
5. Widget configuration accepted any non-invalid integer without checking that the launcher-owned ID belongs to `CommuteWidgetReceiver`.

Adjacent minor: widget errors were persisted using Kotlin runtime class simple names rather than explicit stable tokens.

## RED evidence

Tests were added before production changes for the five findings and the stable-token minor.

Initial focused command:

```text
.\gradlew.bat :app:testDebugUnitTest \
  --tests com.rafaam11.businfo.ui.map.NaverMapOverlayContractTest \
  --tests com.rafaam11.businfo.widget.WidgetStatusFormatterTest \
  --tests com.rafaam11.businfo.widget.CommuteWidgetRepositoryTest \
  --tests com.rafaam11.businfo.ui.BusAppViewModelTest \
  --tests com.rafaam11.businfo.widget.WidgetErrorTokenTest \
  --tests com.rafaam11.businfo.widget.CommuteWidgetContractTest --no-daemon
```

Result: `FAILED` during test compilation, as expected for the wished-for APIs/contracts. Missing symbols reported:

- `BusAppViewModel.beginKeyChange`
- `AppUiState.NeedsKey.changeMode`
- `widgetStatusLabel`
- `WidgetErrorToken`

The pre-implementation tests also fixed the expected contracts for:

- `marker.anchor = PointF(0.5f, 0.5f)` while retaining direct `vehicle.point.toLatLng()` positioning and prohibiting snap/project helpers.
- a newer Room `fetchedAt` clearing a prior stored widget error without a dashboard refresh call.
- no `EXTRA_OPEN_KEY_SETTINGS` trust in exported `MainActivity`, a non-exported trampoline, and one-shot request consumption.
- invalid and unowned app-widget IDs returning `RESULT_CANCELED` before UI or preference writes.

First GREEN attempt reached test execution and reported `33 tests completed, 1 failed`. The only failure was a test-fixture assertion expecting `2정거장 · 약 2분`; the established domain formatter returns `2정거장 전`. The assertion was corrected without changing production behavior.

## GREEN evidence by finding

1. Center rotation anchor
   - `NaverMapOverlayContractTest.vehicleMarkersUseRenderedFlatHeadingAwareIconsWithoutSnappingCoordinates`
   - Explicit center anchor, direct API coordinate assignment retained, no snapping/movement helper introduced.

2. Failure age
   - `WidgetStatusFormatterTest`: 45 seconds, 2 whole minutes from 125 seconds, and future-clock skew clamped to 0 seconds.
   - Pure `widgetStatusLabel(state, now)` produces `갱신 실패 · <elapsed>` from `refreshErrorAt`.
   - Invalid-credential action remains `API 키 변경` and targets the private trampoline.

3. Obsolete errors
   - `CommuteWidgetRepositoryTest.newer room snapshot suppresses and clears obsolete widget refresh error`
   - Asserts fresh arrival remains visible, returned error fields are null, preference error is cleared, and no refresh/network boundary is called by `state()`.

4. Trusted non-destructive key replacement
   - `CommuteWidgetContractTest.activityConsumesOnlyPrivateOneShotKeyRequestAndIgnoresMaliciousExtra`
   - `CommuteWidgetContractTest.keySettingsTrampolineIsNotExported`
   - `KeySettingsRequestStoreTest.trustedRequestIsConsumedExactlyOnce` (Android test compiled; device execution unavailable).
   - `BusAppViewModelTest.failed replacement validation preserves old key`
   - `BusRepositoryTest.failedReplacementLeavesPreviouslySavedCredentialUntouched`
   - Merged manifest confirms `WidgetKeySettingsActivity exported=false`; exported `MainActivity` contains no key-settings extra contract.

5. Configuration ownership
   - `CommuteWidgetContractTest.configurationRejectsIdsNotOwnedByThisWidgetProvider`
   - `CommuteWidgetConfigurationTest.invalidWidgetIdIsRejectedBeforeUi`
   - `CommuteWidgetConfigurationTest.unownedWidgetIdIsRejectedBeforeUiAndSave`
   - Launcher-required configuration activity remains exported. A success instrumentation case was not retained because arbitrary IDs are intentionally no longer valid and allocating/binding an owned ID requires launcher/system binding authority.

6. Stable error tokens
   - `WidgetErrorTokenTest` covers all explicit snake-case write tokens and legacy simple-name parsing.
   - Existing `Unsafe` usage was not expanded; new token tests use the pure codec directly.

Focused GREEN command combined the six focused unit suites and Android-test compilation. Result: `BUILD SUCCESSFUL`; 33 focused unit tests passed and Android-test Kotlin compiled.

## Full verification

Command:

```text
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL in 58s`.

- Full unit suite: 125 tests, 0 failures, 0 errors, 0 skipped across 26 suites.
- Android-test Kotlin compilation: passed.
- `assembleDebug`: passed.
- `git diff --check`: exit 0 (only Git's existing LF-to-CRLF checkout warnings).
- Merged-manifest inspection: configuration activity exported `true`; key-settings trampoline exported `false`; widget receiver exported `false`.
- `adb devices`: command found in the standard Android SDK; device list was empty, so connected instrumentation tests were not executed.

## Self-review

- No network call was added to passive widget `state()`.
- Stored error removal is strictly ordered: only a snapshot with `fetchedAt` newer than `refreshErrorAt` suppresses it.
- Key replacement uses the existing validate-then-write repository boundary. Failed validation, back-out, or process restart never clears the old key.
- No externally supplied key-settings boolean is consumed by `MainActivity`; only the app-private one-shot store is consumed.
- Widget invalid-credential behavior remains actionable rather than degrading to a generic refresh action.
- Ownership rejection happens before `setContent` and before `saveSlot`; default result remains canceled.
- No marker coordinate projection, interpolation, or snapping was introduced.

## Remaining device-only checks

With a connected launcher/device, execute the compiled Android tests to confirm PendingIntent-to-non-exported-activity delivery, one-shot persistence on the platform SharedPreferences implementation, and launcher-owned widget configuration success. No device was attached in this run.
