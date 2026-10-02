# Must implementation validation — September 30, 2026

## Delivered scope

M1–M6 are implemented in the Android app and a new native SwiftUI iOS host. The shared module contains logging, validation, reminder eligibility, serialization, and estimate rules. The Should/Could plans and the explicitly excluded features remain outside this change.

## Automated evidence

| Check | Result |
| --- | --- |
| Android debug assembly, test assembly and lint | Passed; lint has 0 errors and 12 dependency/style warnings |
| Shared Kotlin/JVM tests | 10 passed |
| Android 16 / API 36, Pixel 9 Pro emulator | 9 instrumentation tests passed |
| iOS 26.2, iPhone 17 Pro simulator, Xcode 26.2 | 3 native unit tests and 1 UI test passed |
| iOS simulator framework and host build | Passed |
| Unsigned iOS arm64 device framework and host build | Passed; no physical installation or signing tested |

The shared tests cover dose validation, unsupported profiles, relative-curve behavior, separation by medication identity, carryover, CSV escaping, and stale reminder actions. They test software behavior, not clinical validity.

Android tests cover populated v1 migration, custom medication units, immutable snapshots, action deduplication and tombstones, wrong/tampered passwords, rollback after invalid restore, recovery snapshots, reminder expiry/replay/follow-up bounds, iOS backup decryption, logging/Undo, activity recreation, history editing/deletion, daylight-saving date filtering, CSV and multipage PDF reports. After the final transaction-order fixes, the five Must data/reminder tests and German UI flow were rerun against the final APK: all six passed. A separate UI flow logs a dose and reaches Export in German, dark mode and 1.5× text scaling, then restores its settings.

Native iOS tests cover Android backup decryption with a Unicode passphrase, native encryption round trips, tampering/wrong passwords/truncation, persistence after reopening, duplicate action IDs, Undo tombstones, immutable history, invalid restore without mutation, restored reminders disabled, future schema rejection, formatting and long-report pagination. XCUITest exercises English logging and Undo, then German logging at the largest accessibility text category. Its final run used dark mode.

Synthetic binary fixtures test both Android→iOS and iOS→Android backup compatibility. Their public test-only password and envelope are documented in `docs/BACKUP_FORMAT.md`.

## Visual review

Reviewed Android Home in English/light and German/dark at 1.5× text scaling, plus the first page of its generated multipage PDF. Reviewed iOS German Home/form screenshots at the largest accessibility category, including dark mode. The iOS logging button initially truncated at that size; it now wraps, and actions scroll with content at accessibility sizes. German localization initially lacked a bundle declaration; both languages are now declared and the UI test passes.

Saved evidence in `artifacts/must/`:

- `android-home.png`
- `android-de-dark-large.png`
- `android-report.png` and `android-report.pdf`
- `ios-de-dark-largest-home.png`
- `ios-de-dark-largest-form.png`

All screenshots and reports contain synthetic data only.

## Build commands

Use JDK 21 for Gradle. Java 25 is incompatible with this pinned build configuration.

```sh
./gradlew :shared:jvmTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/Logbook.xcodeproj -scheme Logbook -configuration Debug \
  -destination 'platform=iOS Simulator,id=3BE42C6D-732F-41E3-92BF-4FF19792B655' \
  -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=NO test
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/Logbook.xcodeproj -scheme Logbook -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath iosApp/buildDevice \
  CODE_SIGNING_ALLOWED=NO build
```

The simulator identifier is specific to the development machine; select an available simulator elsewhere. Logs/results are in Gradle's build reports and Xcode's derived-data test results. Tests explicitly target emulators/simulators and do not require modifying a connected personal phone.

## Limitations and remaining release checks

- Android 17 UI instrumentation hit the installed AndroidX/Espresso `InputManager.getInstance` incompatibility. Android 17 storage/report tests passed, but UI behavior is not certified by this run. One subsequent Android 16 run stopped before test execution after an emulator process crash; the recovered emulator passed all nine tests.
- Minimum-OS boundaries, physical-device installation, OEM power restrictions, overnight reminders, reboot and daylight-saving notification delivery need device validation. The tested daylight-saving case concerns report date filtering, not notification delivery.
- iOS has a finite notification schedule. It queues at most 60 requests across 14 days and exposes coverage in Settings. This is not indefinite background scheduling; users must reopen the app to replenish it.
- Full TalkBack/VoiceOver traversal, hardware keyboard navigation, and a comprehensive contrast/accessibility audit remain unverified. Semantic labels, scalable layouts, visible mood labels, system themes, and selected large-text flows are implemented and tested.
- Native document-picker interaction, low-disk failure injection, and interrupted OS-level file writes need manual/device acceptance beyond the tested encryption, validation and atomic storage paths.
- Independent clinical/regulatory review remains unresolved. No mathematical test or successful build establishes clinical validation.

## Android debug artifact

`artifacts/ADHSLogbook-debug.apk` — version 0.2.0, debug signed.

SHA-256: `be338037c824add7a9dfad17efebe249b88a1c9f5287ac3e77e2d65fb2ec6001`
