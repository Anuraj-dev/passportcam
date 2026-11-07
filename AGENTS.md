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

## Releasing (read this before shipping an APK)
Status: v1.0.0 shipped 2026-11-07. Project is in maintenance mode; builds are done by CI, not locally.
- Repo: https://github.com/Anuraj-dev/passportcam (public). `main` is protected by convention: branch, PR, then merge. Raja merges.
- To release: bump `versionName` + `versionCode` in `app/build.gradle.kts`, merge to `main`, then `git tag -a vX.Y.Z -m vX.Y.Z && git push origin vX.Y.Z`. `.github/workflows/release.yml` runs tests, builds and signs, verifies with apksigner, and publishes `PassportPhoto-vX.Y.Z.apk` + `.sha256` to GitHub Releases. The workflow fails if the tag does not match `versionName`.
- Signing: release signing reads env vars `PASSPORTCAM_KEYSTORE`, `PASSPORTCAM_KEYSTORE_PASSWORD` (optional `PASSPORTCAM_KEY_ALIAS`, default `passportcam`). Without them the release APK is unsigned. CI gets them from repo secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`.
- **Keystore backup lives in `.release-keys/` (gitignored, mode 600): `passportcam-release.jks` + `passportcam-release.pass` (the password). Original copy: `~/.android/`. NEVER commit, print, or push these. Losing the key means installs can no longer be updated in place (must uninstall first).**
- Reset the secrets if needed (Raja runs these; the agent classifier blocks secret writes):
  `base64 -w0 .release-keys/passportcam-release.jks | gh secret set KEYSTORE_BASE64 -R Anuraj-dev/passportcam` and `gh secret set KEYSTORE_PASSWORD -R Anuraj-dev/passportcam < .release-keys/passportcam-release.pass`
- Signer certificate SHA-256 (for checking an APK): `1fb5b350414dab501c4b181d91dfae4c29b06c878e32ab34214d657763bdac14`. Check with `apksigner verify --print-certs <apk>`.
- Install a release on a phone: `gh release download vX.Y.Z -R Anuraj-dev/passportcam`, then `adb install -r PassportPhoto-vX.Y.Z.apk`. A debug build has a different signature, so uninstall it first (`adb uninstall dev.raja.passportcam`).
- Local builds were cleaned to save disk: `build/`, `.gradle/`, `~/.gradle` were deleted. The first `./gradlew` run re-downloads about 1.6 GB (Gradle 9.7.1, dependencies) and may need Android SDK platform 37 (`compileSdk = 37`). Needs JDK 21.
- README screenshots are `docs/images/screens.png` (emulator captures; the group photo is NASA group 21, public domain).
