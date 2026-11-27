# Release signing

Every release must be signed with the **same key**, or updates fail and the
only way forward is uninstalling (which deletes the user's library and
progress). Treat the keystore as irreplaceable.

## 1. Create the keystore (once)

Keep it outside the repository:

```bash
mkdir -p ~/.android-keys && chmod 700 ~/.android-keys
keytool -genkeypair -v \
  -keystore ~/.android-keys/rreader-release.jks \
  -alias rreader -keyalg RSA -keysize 4096 -validity 36500
```

Back up the `.jks` file and both passwords to a password manager or an
encrypted backup. Without them the app can never be updated again.

## 2. Point Gradle at it (outside the repository)

Add to `~/.gradle/gradle.properties` (user-level, never committed):

```properties
rreader.signing.storeFile=/absolute/path/to/.android-keys/rreader-release.jks
rreader.signing.storePassword=...
rreader.signing.keyAlias=rreader
rreader.signing.keyPassword=...
```

The app build reads these properties; when they are missing, release builds
are left unsigned and cannot be installed.

## 3. Build and verify

```bash
./gradlew :app:assembleRelease
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Check that the certificate digest matches the previous release before
installing ([install-on-device.md](install-on-device.md)).

## 4. Versioning

- Increase `versionCode` for every build installed on a phone.
- `versionName` follows Semantic Versioning and matches the
  [CHANGELOG](../../CHANGELOG.md) entry for the release.
