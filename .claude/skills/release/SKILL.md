---
name: release
description: Produce a signed R Reader release and install it on the primary user's phone. Use at the end of a roadmap phase or when asked to ship a new version.
---

# Release

1. Confirm `main` is green in CI and `./gradlew check` passes locally.
2. Bump `versionCode` (always) and `versionName` (SemVer) in
   `app/build.gradle.kts`; move the `CHANGELOG.md` "Unreleased" entries under
   the new version.
3. Make sure signing is configured outside the repo
   ([docs/runbooks/release-signing.md](../../../docs/runbooks/release-signing.md))
   and that `secrets.properties` sets `CATALOG_CONTACT` for the catalogue's
   User-Agent ([docs/runbooks/secrets.md](../../../docs/runbooks/secrets.md)),
   then run `./gradlew :app:assembleRelease` and verify the certificate with
   `apksigner verify --print-certs`.
4. Run the AI-Generated Code Security Auditor and UI Finish-Gate Reviewer
   agents on the changes since the previous release.
5. Install the release APK (never a debug build: it is debuggable, and
   replacing it later forces an uninstall that deletes the library) with
   `adb install -r` following
   [docs/runbooks/install-on-device.md](../../../docs/runbooks/install-on-device.md)
   and run the device checklist. Then, on the phone, turn off Wireless
   debugging and USB debugging, revoke USB debugging authorizations and turn
   Developer options off (runbook step 4), every time.
6. Commit the version bump (`chore(release): vX.Y.Z`) and tag it `vX.Y.Z`.
