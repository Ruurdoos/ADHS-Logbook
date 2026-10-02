# QA remediation — 2 October 2026

The implementation addresses the audit's concrete defects and the five product decisions. Changes are grouped into focused commits on `codex/qa-remediation` for review against `main`. No production data, accounts, or deployment was changed.

## Implemented behavior

| Area | Result |
| --- | --- |
| Medication-free use | Welcome offers Start without medication and Restore backup. Independent observations and measurements require no medication. |
| Unreadable logs | Explicit recovery state replaces indefinite loading. Confirmed restore preserves unreadable local files before rebuilding the log. |
| Sleep | New ratings record quality, from 0 very poor to 4 very good. Original sleep ratings retain their original scale. Sleep dates use a date picker. |
| Non-use | Reminder records describe one scheduled dose. Manual records distinguish completed calendar days from periods. Yesterday is the default completed day; today's record can end now. Calendar boundaries respect daylight-saving changes. |
| Compatibility | Backup schema 4 reads schemas 1–3. New periods use exclusive end timestamps; old records keep inclusive endpoints. Reports include the record kind and scale version. |
| iOS corrections | Existing entries allow medication correction, require amount review, retain unchanged medication snapshots, and reconcile supply and reminder links. Editing never exposes creation Undo. |
| Widgets and lock | A widget opens review whenever app lock is enabled, even after authentication. It cannot silently write a dose behind an Open label. |
| Reports | Separate Create and Share actions. Changes to options or records invalidate prepared files; stale background results cannot become shareable. |
| Android backups | Choose the file before entering the passphrase. Secrets are not saved in instance state. Backup work and result state survive activity recreation. |
| Write reliability | Android queues writes instead of silently dropping preferences. iOS serializes writes, allocates dose identities inside the write boundary, and guards duplicate actions. |
| Reminders | No automatically invented reminder time. Empty schedules, paused reminders and denied notification permission have explicit messages. Duplicate times receive actionable errors. |
| Drafts | Nested Android observation/measurement and dose/non-use flows retain drafts. Editors require explicit discard when leaving. iOS sheets prevent accidental swipe dismissal. |
| History | Search medication names and notes, filter by medication and date, and reactivate archived medications. |
| Supply | Separate restock, recount and settings actions. Settings can change without creating a new physical count. Home supply links preselect their medication. |
| Wording and accessibility | Clearer logging actions, a Home-only observation-switch label, persistent iOS dose-unit labels, rating descriptions, and accessible Android control names. New strings have German translations. |
| Performance | Android composition reads ViewModel snapshots instead of querying the database. Record lists are lazy. iOS loading, validation, persistence, charts and weekly calculations run off the main thread. |
| Test infrastructure | AndroidX runner 1.7.0, JUnit extension 1.3.0 and Espresso 3.7.0 remove the Android 17 InputManager reflection failure. UI tests use isolated fixtures. |

The non-use decision is intentionally conservative: a missed scheduled dose does not assert that no other dose was taken at that moment. Missing records always remain unknown. Disabling the observation Home shortcut does not disable observations in History.

## Validation

- Shared domain tests: 27 passed.
- Android 17 emulator: 28 instrumentation tests passed, including medication-free onboarding, nested drafts, corrupt-record recovery, daylight-saving boundaries, schema 4 backup round trips, stock settings, and existing logging flows.
- Android 16 temporary emulator: 28 instrumentation tests passed. The final dose-draft/lifecycle cleanup change was subsequently checked on Android 17.
- Android lint: 0 errors, 31 warnings, 1 hint. Remaining warnings include newer production dependency versions, API-specific widget attributes and existing platform/style notices. This change upgrades the test dependencies needed for the reproduced failure; it does not claim all production dependencies are current.
- iOS 26.2 simulator: 17 unit tests and 4 UI tests passed. The new UI test verifies medication-free onboarding and a saved sleep-quality rating. The Debug project and generator now explicitly enable isolated UI-test data. Final result: `iosApp/build/Logs/Test/Test-Logbook-2026.10.02_14-03-21-+0200.xcresult`.
- Whitespace/error-marker check: `git diff --check` passed.

Commands used: `:shared:jvmTest`, `:app:connectedDebugAndroidTest`, `:app:lintDebug`, and `xcodebuild test` with the Logbook scheme and iPhone 17 Pro / iOS 26.2 simulator. Android runs targeted only emulator serials; the connected physical phone was not used.

### Synthetic large-log measurements

| Platform | Records | Restore | Summary |
| --- | ---: | ---: | ---: |
| Android 16 emulator | 1,000 | 521 ms | 9 ms |
| Android 16 emulator | 10,000 | 852 ms | 42 ms |
| iOS 26.2 simulator | 1,000 | 177 ms | 26 ms |
| iOS 26.2 simulator | 10,000 | 1,797 ms | 264 ms |

These are development-machine samples, not release-device guarantees or direct platform comparisons. Android's benchmark counts one encompassing summary interval; iOS uses the full calendar-day summary. The 100,000-record format limit was not load-tested.

## Remaining verification limits

Real-device biometrics, notification delivery under battery restrictions, TalkBack/VoiceOver traversal, external file-provider behavior and physical-device performance still need manual acceptance testing. Automated large-text/German UI flows pass, but do not establish complete accessibility compliance. Minimum supported Android/iOS versions were not available in this environment.

Xcode's successful test runs also emitted an `IOSurfaceClientSetSurfaceNotify` warning and a post-test diagnostic-collection `simctl` lookup warning because the host's default developer directory is CommandLineTools. The build and test commands explicitly selected Xcode; those warnings did not fail tests. An earlier Android 17 run encountered a transient ActivityScenario STOPPED-state launch timeout; a complete subsequent run passed.

New schema-4 backups cannot be restored by older app builds that only understand schema 3. Preserve older backups until both devices have been updated. No existing sleep ratings or legacy non-use endpoints are reinterpreted.

## Source references

- [Shared record rules](../shared/src/commonMain/kotlin/com/adhs/logbook/shared/ShouldModels.kt)
- [Android write and backup lifecycle](../app/src/main/java/com/adhs/logbook/LogbookViewModel.kt)
- [Android screens and navigation](../app/src/main/java/com/adhs/logbook/MainActivity.kt)
- [Android observation, non-use and supply forms](../app/src/main/java/com/adhs/logbook/ShouldScreens.kt)
- [iOS persistence and recovery](../iosApp/Logbook/LogbookStore.swift)
- [iOS entry editing and onboarding](../iosApp/Logbook/LogbookApp.swift)
- [Backup format](../docs/BACKUP_FORMAT.md)
- [Report format](../docs/REPORT_FORMAT.md)
- [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)
