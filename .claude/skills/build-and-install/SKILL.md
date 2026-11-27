---
name: build-and-install
description: Build, test and install R Reader on a connected Android phone. Use when asked to run the app, verify a change on a device, or run the local quality gate.
---

# Build, test and install

1. Run the local gate: `./gradlew check` (unit tests, Android Lint, detekt,
   Spotless). Fix failures before anything else; `./gradlew spotlessApply`
   fixes formatting.
2. Build a debug APK: `./gradlew :app:assembleDebug`.
3. Connect the phone and confirm `adb devices` lists it; follow
   [docs/runbooks/install-on-device.md](../../../docs/runbooks/install-on-device.md)
   for USB, wireless debugging and Samsung-specific steps.
4. Install: `./gradlew :app:installDebug` (debug) — release builds follow the
   `release` skill.
5. For user-facing changes, walk through the device checklist in
   [docs/testing.md](../../../docs/testing.md).

Notes: Gradle provisions its own JDK 21 (see `gradle/gradle-daemon-jvm.properties`);
any Java 17+ runtime can start the wrapper. Do not run several heavy builds in
parallel on the same machine.
