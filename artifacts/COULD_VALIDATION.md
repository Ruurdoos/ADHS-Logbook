# Could implementation validation — October 1, 2026

## Delivered scope

C1–C4 are implemented on Android and iOS in version 0.4.0. These features are optional and disabled by default. Android storage is schema 4; portable backup documents and typed report CSV are schema 3. Readers retain support for backup schemas 1 and 2. The existing Must and Should features remain included.

| Recommendation | Implementation |
| --- | --- |
| C1: shortcuts and intents | Generic Android launcher shortcut, explicitly configured medication/pinned shortcut, and localized iOS App Intent with optional opaque entity; review-only routing, current authentication/activity/revision checks, no external amount input, configuration reconciliation and restore reset |
| C2: optional system surfaces | Android Quick Settings tile with explicit native installation prompt; iOS circular/rectangular Lock Screen widget families; generic labels, optional absolute last-log timestamp, app-lock redaction |
| C3: manual measurements | Pressure with explicit systolic/diastolic mmHg fields, pulse in bpm, weight in kg/lb; separate event/creation timestamps, timezone, notes, editing/deletion, original-value retention, display-only conversion, backup/report support |
| C4: weekly overview | Optional History entry to a chosen calendar week, shared report coverage/counts, daily details and notes, observation distributions, separate legacy dose moods, explicit non-use versus unknown days |

No excluded recommendation was added: best-time/dose insights, predicted crash alerts, voice-assistant logging, full watch apps and alternate fonts remain absent. iOS exposes a review action for explicit Shortcuts use; it does not expose an immediate-save logging action or add assistant phrases.

## Automated evidence

| Check | Result |
| --- | --- |
| Shared Kotlin/JVM | 24 tests passed, including 6 Could tests |
| Android debug/test assembly and lint | Passed; 0 lint errors, 33 warnings and 1 hint |
| Android 16 / API 36, dedicated Pixel 9 Pro emulator | Full suite: 23 instrumentation tests passed; optional-feature UI test passed again after final accessibility/localization changes |
| iOS 26.2 / iPhone 17 Pro simulator | 12 unit tests and 3 UI tests passed across the full run and final unit run |
| iOS simulator app and widget extension | Ad-hoc signed build passed with Keychain and App Group access |
| iOS arm64 app and widget extension | Unsigned device build passed; no physical installation |

Total: **62 unique passing tests**. Repeated regression runs are not added to the count. Existing Must and Should tests are included. The dedicated Android emulator was explicitly selected, then shut down; the user's other running emulator and personal devices were not used.

New checks cover typed measurement validation, nonfinite/zero/mismatched units, display-conversion overflow and round trips, additive schema-3-to-4 migration, persistence/reopening, editing/deletion, invalid-write/restore rollback, original units and creation timestamps, both schema-3 encrypted backup directions, measurement-only coverage, CSV note exclusion, PDF measurement details, seven-day and daylight-saving boundaries, distinct medication snapshots, ordinal response counts and legacy moods. Android shortcut tests verify opt-in, opaque identifiers, invalid input, stale revisions, archival and disabling. iOS tests invoke the App Intent, validate entity tokens, exercise its App Group queue and authentication gate, reject arbitrary input, check stale review and restore reset, and verify widget URLs never save doses.

Native UI tests create a blood-pressure record through explicitly labelled fields and open the weekly overview. Android also checks the German weekly view after locale recreation. Existing regression tests exercise German, dark mode and large text. Screenshots and synthetic PDFs are in `artifacts/could/`.

## Integration and visual review

- Added the Android Quick Settings tile through Settings → Quick access and the actual system confirmation. Its generic “Log dose” label appears without a “Taken” state. Tapping it opens the prefilled editor.
- Force-stopped the Android app and launched its review intent with `review_token=generic` and an untrusted `dose=999` extra. The editor showed the saved 10 mg amount. Database dose count remained 0 before and after navigation.
- Reviewed Android English/German weekly screens and the iOS dark weekly screen. Added explicit accessibility labels to the new Android settings switches and conversion checkbox.
- Rendered and inspected both platforms' measurement-only PDFs. Values, pressure components, units, coverage and notes were visible without clipping. An iOS test initially expected an English decimal point; it was corrected to use the actual localized number after verifying that the PDF properly showed `151,25 lb`.
- Synthetic schema-3 fixtures use the public test-only passphrase `could passphrase`; no real patient data is present.

Initial validation caught a Swift type-check timeout in the weekly view, a guarded legacy Android tile API lint error, and UI selectors that targeted labels rather than controls. The view was split into smaller components, the pre-API-34 fallback received a narrow lint suppression, and selectors were corrected. Final checks pass.

Xcode emitted a post-test diagnostics-collection warning because the machine's global developer directory points to Command Line Tools. Explicit `DEVELOPER_DIR` builds and test execution succeeded; the warning concerns auxiliary diagnostics, not the test results. Global Xcode settings were not changed.

## Reproduction

Use JDK 21 and select test devices explicitly.

```sh
./gradlew :shared:jvmTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s YOUR_EMULATOR install -r app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_EMULATOR install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s YOUR_EMULATOR shell am instrument -w com.adhs.logbook.quietsage.test/androidx.test.runner.AndroidJUnitRunner
```

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/Logbook.xcodeproj -scheme Logbook -configuration Debug \
  -destination 'platform=iOS Simulator,id=YOUR_SIMULATOR' \
  -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/Logbook.xcodeproj -scheme Logbook -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath iosApp/buildDevice \
  CODE_SIGNING_ALLOWED=NO build
```

## Remaining release checks

Physical biometric/device-credential behavior, cancelled unlocks on real devices, pinned-shortcut behavior across launchers, iOS Shortcuts/Lock Screen gallery installation, locked-device timeline redaction across OS versions, and minimum-OS coverage still require device acceptance. The App Intent, App Group queue, lock policy and widget extension are built/tested, but the iOS Lock Screen gallery was not exercised. Full VoiceOver/TalkBack review and clinician review of report usefulness remain release checks.

The Android tile was exercised on API 36; its older Intent overload is guarded below API 34 and has not been exercised on an older emulator. Production iOS distribution still requires a signing team and matching App Group entitlements on both targets.

## Artifact

`artifacts/ADHSLogbook-debug.apk`: versionName `0.4.0`, versionCode `4`, debug build.

SHA-256: `aef74c3799b76e596e5213c6ea3231241a844d378c287689445bcb36a8c90a18`.
