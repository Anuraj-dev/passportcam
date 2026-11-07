# PassportCam plan

Locked decisions: Android only, Kotlin + Compose; fully on-device; India passport 51x51 mm square (600x600 px @300dpi); keep original background; quality-first then shrink to <100 KB (95->60 quality, then 600->500->413 px); group = every face above min size (120 px); output to Pictures/PassportPhotos via MediaStore (must show in picker Recents); personal sideload APK.

Modules: A scaffold/nav/theme (driver+ui), B camera (ui), C loader+detector+cropper, D compressor, E saver, F results UI, driver: PassportProcessor orchestrator.

Milestones: 1 single person camera+upload -> saved <100KB visible in Recents. 2 group photos. 3 edge cases (no face, edge crops, rotated).

Testing: emulator AVD `passportcam` first (will announce), then Raja's phone over USB debugging when emulator tests pass.
