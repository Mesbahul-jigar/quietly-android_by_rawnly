# Quietly — Android microphone monitor

Android adaptation of the supplied Quietly project. Flutter UI with a Kotlin audio engine.

## Build status

Source and configuration were inspected and patched. Compilation and Flutter tests
were NOT completed: automatic security review blocked Flutter startup because it
attempted access to an internal cloud metadata endpoint. No APK is included.
The workflow below is provided to compile and test on a normal build runner.

## What this app does

Captures the **phone microphone**, applies available Android noise suppression and
an energy-based noise gate with high/low-pass filters, then plays the result to
connected headphones. Includes suppression, power saver and monitor modes, English
and Arabic UI, and a microphone foreground-service notification.

It does not create true acoustic ANC, alter music from other apps, or replace the
microphone input of Zoom, WhatsApp, Meet or phone calls. It does not use DCCTN or
any trained AI model. Bluetooth headphone output is supported; Bluetooth microphone
capture is not implemented. This distinction matters if your goal is meeting-mic cleanup.
See https://developer.android.com/media/platform/sharing-audio-input for Android's
microphone sharing constraints.

## Get an APK using GitHub Actions

1. Create a GitHub repository and upload the contents of this directory to its root.
   Include the `.github` directory; `pubspec.yaml` should be at the repository root.
   Do not upload only this ZIP as a single repository file.
2. Open **Actions → Build Android APK → Run workflow** on the default branch.
   A push to `main` or `master` also starts it.
3. Wait for a successful run. Open it and download **Quietly-Android-APK** under
   **Artifacts**. Extract the artifact ZIP to get `app-release.apk`.
4. Transfer the APK to your Android phone and open it to install. If prompted,
   allow installation from the specific file manager/browser used to open it.

This workflow runs analysis, existing unit tests and a universal release-mode APK
build. It fails visibly on errors. The APK uses a debug signing key for personal
sideload testing; it is not a Play Store release. Separate CI runs may use different
debug keys, requiring uninstall before reinstall. Configure a permanent private
release key before distributing updates.

## Local Windows build

Install Flutter **3.35.7**, JDK **17**, and Android SDK command-line tools/platform 36
through Android Studio. Add Flutter `bin` to PATH. Run `flutter doctor` and accept
Android SDK licenses with `flutter doctor --android-licenses`. Then double-click
`build-android.bat`, or run it from a terminal in this directory.

APK output: `build/app/outputs/flutter-apk/app-release.apk`.

The project targets Android 16/API 36 and requires Android 8/API 26 or later.
The minimum was raised to match the foreground-service/audio APIs used by this project.

## Phone verification still required

- Connect headphones before starting; use low headphone volume for the first test.
- Grant microphone access. Notification permission is optional.
- Speak near the phone microphone. Compare Monitor with Suppression.
- Switch modes while running and verify the audio restarts cleanly.
- Deny/revoke microphone permission and verify an error is shown.
- Disconnect headphones: the engine should stop when the output route changes.
- Stop monitoring and confirm the microphone indicator and notification disappear.
- Test screen lock, app switching and closing the app on your phone.

Audio ends when the Android activity is destroyed; it does not auto-restart after
process death or reboot. It is not a resilient background recording service.
The displayed delay is a buffer-duration estimate, not measured end-to-end latency.
The animated bars respond to level; they are not a frequency spectrum.
Suppression quality, Bluetooth delay and battery consumption remain unmeasured.

## License

Original MIT license retained in LICENSE.
