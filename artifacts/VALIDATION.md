# Build validation — September 29, 2026

- Native Android debug APK built successfully.
- 8 shared Kotlin/JVM tests passed.
- 3 Android instrumentation tests passed on Pixel 9 Pro emulator, Android 16 / API 36.
- Lint: 0 errors; 11 warnings (dependency updates and a Kotlin extension style suggestion).
- Rendered home screen and PDF first page reviewed; report also exercised pagination with long Unicode notes.
- APK permissions inspected: notifications, boot completion, and AndroidX's app-local receiver permission. No Internet permission.
- Final APK installed on `emulator-5554` successfully. Instrumentation test fixtures were removed with the test installation; final installation starts fresh.

APK: `ADHSLogbook-debug.apk`

SHA-256: `d9c030cd0f7da063e5a3075fe194bb23f6823598e7fa26bef31378d95798735b`

Checks run:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:assembleDebug :shared:jvmTest :app:connectedDebugAndroidTest :app:lintDebug
```

The initial connected-device run also discovered an Android 17 Pixel 6a. Its UI test failed in the AndroidX test framework (`InputManager.getInstance`); its storage/report test passed. Subsequent checks explicitly targeted the emulator. Android 17 UI behavior is unverified. Test installations were automatically removed by Gradle after each test run.

Reminder scheduling and reboot receivers compile and pass static checks; overnight delivery and physical-device battery restrictions have not been tested. iOS framework generation and an iOS app are outside this verified Android build. Pharmacokinetic curves are simplified relative estimates and have not been clinically validated.

The images in this directory contain synthetic test data only.
