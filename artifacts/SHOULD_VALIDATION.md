# Should implementation validation — October 1, 2026

## Delivered scope

All six Should recommendations are implemented on Android and iOS, following the Must host and storage work. Android is version 0.3.0, database schema 3; iOS is version 0.3.0 with backward-compatible additions to its private document. Shared portable documents now use schema 2 and retain schema-1 readers.

| Recommendation | Implementation |
| --- | --- |
| S1: optional privacy lock | AndroidX Biometric/device credentials and iOS LocalAuthentication; protected lock preference, background relocking, app-switcher shielding, current-policy checks on external actions, draft retention and explicit exported-file limits |
| S2: home-screen widget | Glance provider and WidgetKit/AppIntent extension; explicit medication selection, generic default, absolute last-log time, revision/token checks, durable writes/Undo, App Group file coordination and cross-process notification |
| S3: observations | Independent records, seven categories, explicit rated/none/unsure/recorded-time responses, optional dose links, sleep-night date, edits/deletion, original timestamp context, optional Home shortcut |
| S4: reports | Shared coverage/count model, medication snapshot groups, summary-first PDF, appointment questions, note exclusion, chronological daily details, accessible preview, typed CSV schema 2 |
| S5: supplies | Physical counts/restocks, explicit package mapping or dose-unit consumption, reconciliation after edits/deletion/Undo, negative/incomplete-count handling, separate opt-in deduplicated stock/prescription alerts |
| S6: pause/non-use | Manual/timed pause, invalidated old occurrences, future-only resumption, explicit separate non-use points/periods, conflict validation, reports and backup support |

The explicitly excluded features and the Could scope were not implemented.

## Automated evidence

| Check | Result |
| --- | --- |
| Shared Kotlin/JVM | 18 tests passed |
| Android debug/test assembly and lint | Passed; 0 errors and 27 dependency/style/platform-attribute warnings |
| Android 16 / API 36, dedicated Pixel 9 Pro emulator | 17 instrumentation tests passed across the full suite and final regression runs |
| iOS 26.2 / iPhone 17 Pro simulator, Xcode 26.2 | 8 unit tests and 2 UI tests passed |
| iOS simulator app + widget extension | Ad-hoc signed build passed, including App Group access |
| iOS arm64 app + widget extension | Unsigned device build passed; no physical installation |

Total: 45 passing tests. Existing Must regression tests remain included. Android was tested using an explicitly selected emulator, without installing or deleting anything on a connected personal phone.

New tests cover independent observations, explicit response semantics, malformed records and overlap rollback, pause deadlines and future-only resumption, ledger fractions/recounts/backdating/restocks, duplicate actions and Undo, persistence/restore, restored supply alerts disabled, current-lock rejection of old reminder/widget actions, stale widget configurations, both schema-2 backup directions, note-redacted typed CSV, empty/sparse summary coverage, a 25-hour reporting day, and multipage PDFs. The native widget test verifies coordinated App Group files, token consumption, duplicate rejection, lock gating and medication revision changes. UI tests save an observation without adding a dose and reach report options; existing logging tests also cover German, dark mode and large text.

Schema-1 and schema-2 fixtures contain synthetic data only. See [backup specification](../docs/BACKUP_FORMAT.md) and [report fields](../docs/REPORT_FORMAT.md).

## Visual and integration review

- Pinned the actual Android Glance widget through Settings and the launcher confirmation. Its generic content rendered correctly; tapping it opened the selected medication's dose editor without creating a dose.
- Reviewed Android's German summary PDF, including explicit non-use versus missing records and long appointment questions across pages.
- Reviewed iOS summary PDF and corrected arbitrary word splitting; wrapping now respects word boundaries. Reviewed the independent-observation list and existing German/dark/large-text flows. The observation navigation title was shortened to fit the native title area.
- Android Should editors hide bottom navigation while open, matching the existing editor behavior; Back returns to the originating screen.
- Saved screenshots and synthetic reports in `artifacts/should/`.

During validation, unsigned simulator execution correctly failed closed when Keychain entitlements were unavailable. Ad-hoc signing resolved this and enabled App Group tests. Two initial UI tests used incorrect selectors; selectors were corrected and both flows passed. An Android emulator showed a System UI “not responding” prompt after the test run; dismissing it allowed launcher/widget review. This was not an application crash.

## Reproduction

Use JDK 21. Select a simulator/emulator explicitly; identifiers below are machine-specific.

```sh
./gradlew :shared:jvmTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
```

The final complete Android test run used direct `adb -s emulator-5556 install -r` for the app and test APKs, then `adb -s emulator-5556 shell am instrument -w com.adhs.logbook.quietsage.test/androidx.test.runner.AndroidJUnitRunner`. This retained synthetic report files for visual inspection. Gradle's connected test runner removes installed test artifacts after execution.

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/Logbook.xcodeproj -scheme Logbook -configuration Debug \
  -destination 'platform=iOS Simulator,id=3BE42C6D-732F-41E3-92BF-4FF19792B655' \
  -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/Logbook.xcodeproj -scheme Logbook -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath iosApp/buildDevice \
  CODE_SIGNING_ALLOWED=NO build
```

## Remaining release checks

Implementation and simulator checks do not establish physical biometric/device-credential behavior, enrollment changes, app-switcher behavior on every OS, overnight notification delivery, OEM battery restrictions or minimum-OS coverage. Those need device acceptance. Full VoiceOver/TalkBack review and clinician review of report usefulness remain external checks.

The iOS widget extension builds and its shared-container command path is tested; adding/resizing it in the iOS home-screen gallery and testing a provisioned physical device remain unverified. Both iOS targets need the same App Group provisioned under the developer's team. Widgets use one app-selected configuration for all instances. Widget refresh timing is controlled by the OS, so already-rendered content can persist until a refresh.

iOS reserves 48 dose requests across 14 days and 12 supply requests, selecting earliest queued supply dates. Reopening the app refreshes coverage; delivery is not indefinite while the app remains unopened. Android alarms are inexact. Paused prescription dates are skipped rather than replayed as an overdue backlog. Supply notifications are an explicit separate opt-in and remain off after restore.

The lock protects app access and previews; it does not revoke exported PDFs/CSVs or claim whole-database encryption. Reports are unencrypted; backups use the existing authenticated encryption envelope. No stock calculation or observation summary recommends a dose or asserts medication causation.

## Final artifact

`artifacts/ADHSLogbook-debug.apk` — version 0.3.0 (build 3), debug signed.

SHA-256: `52c61cc0cbdc60464d796e28d7f4af97d11d31428e1ad52cb40c6e9380148313`.

After the complete runs, Android SQLite replacement writes were tightened to throw on failure and Should editors' navigation was corrected. The affected 12 data/observation UI tests were rerun against the final APK. The iOS observation UI test was also rerun after shortening its navigation title.

Final prescription-pause fix: resuming before a prescription date now re-arms that future date on Android. All seven Should instrumentation tests passed again against the packaged APK, including the added regression. Final Android assembly and lint passed.
