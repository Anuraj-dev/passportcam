# PassportCam

Android app (Kotlin, Jetpack Compose, minSdk 26). Take or pick a photo -> one 51x51 mm India-passport-style square JPEG per detected face, each **< 100,000 bytes**, saved to `Pictures/PassportPhotos` via MediaStore so it shows in the system file picker's **Recent**. Fully on-device (ML Kit face detection). Original background kept. Personal use, sideloaded APK.

## Users
Non-technical adults who rarely use phones. UI must be: huge buttons, very few words, one obvious action per screen, smooth transitions, no settings, no jargon, no dead ends (every error says what to do next).

## Commands
- Build debug APK: `./gradlew :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Emulator AVD: `passportcam` (own AVD; do NOT touch `voisu_test`). Start: `~/Android/Sdk/emulator/emulator -avd passportcam -no-snapshot-save &` (record the PID).
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`; launch: `adb shell am start -n dev.raja.passportcam/.MainActivity`
- Logs: `adb logcat -s PassportCam` (all app logging goes through `core/PLog`; tag `PassportCam`, `[area]` prefix).

## Architecture (package `dev.raja.passportcam`)
- `core/` `PLog` (logging), `Config` (all tunables: MAX_BYTES, sizes, quality range, min face size).
- `pipeline/` `ImageLoader` -> `FaceDetector` -> `PassportCropper` -> `Compressor`, orchestrated by `PassportProcessor`. Models in `Models.kt`.
- `storage/` `PhotoSaver` (MediaStore save/delete).
- `ui/` Compose screens + ViewModel; calls `PassportProcessor.process(context, uri)` and `PhotoSaver`.

Module contracts are the KDoc on each stub. Respect signatures; if one must change, say so in your report rather than silently diverging.

## Rules
- Log every pipeline step and every error through `PLog` (area = loader, detector, cropper, compressor, saver, camera, ui, processor). Log sizes, timings, face counts, chosen quality/side.
- Size cap is strict: never emit a photo >= `Config.MAX_BYTES`.
- Never block the main thread with decode/ML/encode.
- No network, no analytics, no extra permissions beyond CAMERA.
- Unit-test pure logic (crop maths, compressor loop where possible). Bitmap-dependent tests may be instrumented or Robolectric-free pure-JVM on the maths only.
- Do not commit; the driver commits. Other agents are editing other files in parallel; touch only the files you own.
