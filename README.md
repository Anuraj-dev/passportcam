<h1 align="center">Passport Photo</h1>

<p align="center">
  Passport size photos from your phone, one per face, each under 100 KB.<br />
  Everything happens on the phone. Nothing is uploaded.
</p>

<p align="center">
  <a href="https://github.com/Anuraj-dev/passportcam/releases/latest">Download APK</a>
  ·
  <a href="#installation">Install</a>
  ·
  <a href="#how-it-works">How it works</a>
  ·
  <a href="#build-from-source">Build from source</a>
</p>

<p align="center">
  <img src="docs/images/screens.png" alt="Four Android screens: the home screen with Take Photo and Choose Photo, the camera with the hint Face in the middle, two results at 92 KB and 98 KB with Share and Delete buttons, and the Recent tab of the file picker listing saved photos" />
</p>

<p align="center"><sub>The group photo is NASA astronaut group 21, public domain.</sub></p>

Take a photo, or choose one from your gallery, and Passport Photo cuts a passport size square for every face in it. The squares are 51x51 mm at 600x600 px, the India passport size, and each JPEG stays strictly under 100,000 bytes. The results are saved to `Pictures/PassportPhotos`, so when a government site asks for a photo, you can pick it from the file picker.

This is a personal Android app, distributed as a sideloaded APK. It needs Android 8.0 or newer and asks for one permission, the camera. Face detection runs on the device with ML Kit, and the app declares no internet permission, so it cannot send photos anywhere. There is no account and no analytics.

## What you get

- **Camera or gallery.** Take Photo opens a camera with a switch camera button and tap to focus. Choose Photo opens the system photo picker, which needs no storage permission.
- **One picture per face.** ML Kit finds every face in accurate mode. A group photo gives you one square per person, each cut around that face.
- **Passport framing.** The head fills 74% of the square height. The crop puts the eyes near the middle and leaves room for shoulders below the chin. The original background stays.
- **Each JPEG is under 100,000 bytes.** The compressor binary searches for the highest quality between 95 and 60. If quality 60 is still too big, it shrinks the square to 500 px, then 413 px. The app drops a face that still doesn't fit.
- **Small faces are skipped and counted.** A face whose shorter box side is under 120 px is left out, and the results screen says how many faces were too small.
- **Share or delete from the results.** Share opens the system share sheet. Delete removes the photo from your gallery after you confirm.
- **Saved where file pickers look.** Files go through MediaStore as `passport_YYYYMMDD_HHMMSS_N.jpg` in `Pictures/PassportPhotos`. On Android 10 and newer, they appear in the Recent list of the system file picker.
- **Nothing leaves the phone.** The only permission is `CAMERA`. The manifest has no `INTERNET` permission, and the dependency list has no analytics library.

## Installation

1. Download `PassportPhoto-vX.Y.Z.apk` from the [latest release](https://github.com/Anuraj-dev/passportcam/releases/latest).
2. Open the APK on the phone. When Android asks, allow installs from this source.
3. Optional: verify the download with the `.sha256` file from the same release. On a computer, run:

```sh
sha256sum -c PassportPhoto-vX.Y.Z.apk.sha256
```

The app needs Android 8.0 or newer. A debug build has a different signature from the release APK, so uninstall any debug build before you install a release.

On Android 8 and 9, the app declares no storage permission, so MediaStore may refuse the save. The photos then go to the app's own folder and don't show in Recent. That fallback has not been tested on a device.

## How it works

```
photo from the camera or the picker
  -> ImageLoader      decode at most 4096 px on the longest side, apply EXIF rotation
  -> FaceDetector     ML Kit, accurate mode, bounding boxes only
  -> PassportCropper  square around each face, head 74% of the height
  -> Compressor       binary search for the highest JPEG quality from 95 to 60; try 600, 500, then 413 px
  -> PhotoSaver       MediaStore insert into Pictures/PassportPhotos
```

Every step logs through `PLog` with an `[area]` prefix. Run `adb logcat -s PassportCam` to see the sizes, timings and chosen quality for each face. The tunables, including the size cap, output sides, quality range and minimum face size, live in [`Config.kt`](app/src/main/java/dev/raja/passportcam/core/Config.kt).

The app is written in Kotlin with Jetpack Compose and Material 3. It uses CameraX for the viewfinder, the ML Kit face detection library, ExifInterface for rotation, and MediaStore for saving.

## Build from source

You need JDK 21 and the Android SDK with `compileSdk` 37. The Gradle wrapper downloads Gradle 9.7.1 on the first run, which takes a while.

```sh
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # writes app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s PassportCam             # every pipeline step, with sizes and quality
```

Release builds take their signing key from environment variables. Without them, the release APK is unsigned. The keystore never lives in the repo.

| Variable | Purpose |
| --- | --- |
| `PASSPORTCAM_KEYSTORE` | Path to the release keystore |
| `PASSPORTCAM_KEYSTORE_PASSWORD` | Password for the keystore and the key |
| `PASSPORTCAM_KEY_ALIAS` | Key alias. Optional, defaults to `passportcam` |

```sh
PASSPORTCAM_KEYSTORE=/path/to/release.jks \
PASSPORTCAM_KEYSTORE_PASSWORD=... \
./gradlew :app:assembleRelease
```

## Releasing

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`, then merge the change to `main`.
2. Tag the merged commit and push the tag. The tag must match `versionName`, or the workflow fails.

```sh
git tag -a v1.0.1 -m v1.0.1
git push origin v1.0.1
```

The [Release workflow](.github/workflows/release.yml) runs the unit tests, builds and signs the APK, checks the signature with `apksigner`, and publishes `PassportPhoto-vX.Y.Z.apk` with a `.sha256` file to a GitHub release. It needs two repository secrets. `KEYSTORE_BASE64` holds the base64 encoded keystore, and `KEYSTORE_PASSWORD` holds its password.

## License

Passport Photo is released under the [MIT License](LICENSE).
