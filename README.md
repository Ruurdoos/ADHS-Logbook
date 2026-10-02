# ADHS Logbook

A local medication journal with native Android (Jetpack Compose) and iOS (SwiftUI) apps, sharing Kotlin domain rules. No account, analytics, network permission, or automatic cloud backup. Version 0.4 includes the Must, Should and Could implementation scopes.

## Android

Open the root directory in Android Studio. Requirements: JDK 21, Android SDK 36; Android 8.0 or later. Set `JAVA_HOME` to your JDK 21 installation before running Gradle.

```sh
./gradlew :shared:jvmTest :app:assembleDebug :app:lintDebug
ANDROID_SERIAL=YOUR_DEVICE ./gradlew :app:connectedDebugAndroidTest
```

Select a device explicitly when several are connected. APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug build, not a signed store release.

## iOS

Open `iosApp/Logbook.xcodeproj` and select the `Logbook` scheme. Requirements: Apple Silicon Mac, Xcode, JDK 21; iOS 17 or later. The build phase links the appropriate shared Kotlin framework automatically. Select your signing team for a physical device.

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project iosApp/Logbook.xcodeproj -scheme Logbook \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build
```

Ad-hoc signing on the simulator is required for Keychain and App Group access. For a physical device, configure the same App Group (`group.com.adhs.logbook.shared`) on the app and widget targets with your signing team.

Run `test` with a specific simulator destination to execute native persistence/crypto/report tests and the logging UI test. The checked-in project can be regenerated from the repository root with `python3 iosApp/scripts/generate_project.py`. The shared scheme is maintained separately. Intel simulator support is not configured.

## Implemented

- Four setup presets plus custom medications with name, formulation, optional strength, usual amount, and explicit `mg`, `ml`, or `unit`. No automatic dose conversion.
- Immutable medication snapshots on entries; editing or archiving a medication preserves history. Android upgrades the existing SQLite database additively to schema 4. The new iOS host uses an atomically written private schema-1 document.
- Last logged amount, date/time, and elapsed time above the estimate. Immediate logging, a prefilled editor, optional mood/notes, and Undo. Durable action identifiers prevent repeated external actions from creating duplicate doses.
- Editable history and local PDF/CSV export. PDF curves stay separate by medication identity and historical model; unsupported formulations remain logging-only.
- Generic or explicitly medication-linked reminders with log-now, snooze, open, one optional follow-up, and a cutoff. Restoring a backup leaves reminders disabled until the user enables them.
- Portable passphrase-encrypted backup, validated restore preview, atomic replacement, and a private pre-restore recovery copy. Both platforms read each other's backups.
- English/German text, regional decimal input and time formatting, system dark mode, visible mood labels, large-text layouts, and chart accessibility descriptions. System fonts are used. There is no app-generated confirmation haptic or required animation.

- Optional app lock using device authentication, background relocking, protected app-switcher previews, and lock-aware external actions. The lock setting remains on its original device and is not part of a portable backup.
- Android Glance and iOS WidgetKit widgets. Settings selects one medication shared by all widget instances, with generic content by default. Non-private configurations can log after opening the app; stale configurations open review. Locking always hides widget details and requires authentication. Durable action IDs prevent duplicate delivery.
- Independent, timestamped observations with explicit rated/none/unsure/recorded-time responses, optional dose links, sleep-night date, edits and deletion. An optional Home shortcut keeps the immediate dose path unchanged.
- PDF summary with recorded counts, coverage, medication snapshot groups, appointment questions, note exclusion and chronological detail. CSV schema 2 distinguishes doses, observations and explicit non-use; see [report fields](docs/REPORT_FORMAT.md).
- Optional supply ledgers with physical counts, restocks, explicit package-unit mapping or per-log consumption, edit/Undo reconciliation, and separate opt-in threshold/prescription notifications. Negative or incomplete estimates ask for a recount; they do not suggest doses.
- Manual or timed reminder pause, plus separate explicit non-use records and periods. Missing records remain unknown. Pause cancels dose follow-ups; resumption schedules future occurrences. Restore keeps both dose and supply notifications disabled.

- Optional quick access: Android launcher/pinned shortcuts and Quick Settings tile; iOS tap-triggered App Intent and Lock Screen widget. Generic labels by default. These entry points open review and never save from a URL; medication revisions, archival, restore and authentication are checked. Enable under Settings → Quick access.
- Optional manual measurements: systolic/diastolic blood pressure (mmHg), pulse (bpm), weight (kg/lb), original values/units, separate measurement/creation timestamps, notes, editing and deletion. Conversion is display only; no clinical interpretation. Enable in Settings, then add from Observations.
- Optional weekly History overview: chosen calendar week, daily records, coverage, observation distributions and separate legacy dose moods. Missing days remain unknown. Uses the same shared counts as reports.
- Portable backups use document schema 3, read schemas 1–3, and retain measurements. Typed CSV schema 3 and PDFs include raw measurements and honor note exclusion.

Fresh installs have no sample medication or patient entries. Test fixtures and screenshots use synthetic data only. Deleting the app deletes private data. Users control report and backup destinations, including any cloud-backed document provider. PDF/CSV are unencrypted reports, not restorable backups. A lost backup passphrase cannot be recovered. See [backup format](docs/BACKUP_FORMAT.md).

## Reminder delivery

Reminders are logging prompts, not treatment instructions. Operating-system notification permissions, power settings, time changes, or force-stop behavior can delay or suppress delivery. Android uses inexact alarms and reconciles on launch, reboot, and time changes. iOS reserves up to 48 pending dose requests across a 14-day horizon and 12 supply requests and displays its current coverage in Settings; opening the app or taking an action refreshes coverage. It does not claim indefinite delivery while the app remains unopened.

## Estimate limits

The relative curve is a heuristic visualization, not a clinically validated prediction of measured concentration, effectiveness, or safe timing. Only the explicit Ritalin-type IR and Vyvanse capsule models are enabled. Concerta-type ER, atomoxetine, and custom medications have no curve. Different charts use independent scales.

[MODEL_NOTES.md](MODEL_NOTES.md) records model IDs, sources, and limitations. Mathematical tests do not constitute clinical validation. Independent clinical/regulatory review remains a release dependency.

## Validation and scope

See [Must implementation validation](artifacts/MUST_VALIDATION.md), [Must plan](docs/plans/01-must-implementation.md), and the [earlier Android validation](artifacts/VALIDATION.md). Physical-device notification delivery, assistive-technology review, and supported-version coverage remain separate release checks; simulator testing does not establish them.

See [Should validation](artifacts/SHOULD_VALIDATION.md) and the completed [Should plan](docs/plans/02-should-implementation.md). See [Could validation](artifacts/COULD_VALIDATION.md) and the implemented [Could plan](docs/plans/03-could-implementation.md). The excluded best-time/dose insights, predicted crash alerts, voice logging, full watch apps, and alternate font were not added.

Design references: [screen board](screens.html) and [design handoff](DESIGN.md). The screen board is an illustrative prototype, not the application or a validated clinical model.
