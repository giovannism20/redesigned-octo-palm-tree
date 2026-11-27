# 0001. Native Android app with Kotlin and Jetpack Compose

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The primary user listens on an Android phone (a Samsung) for hours with the
screen locked, often with a Bluetooth headset. The app must keep reading in
the background, show lock-screen and notification controls, react to headset
buttons and phone calls, and use free on-device voices. Distribution is to a
handful of devices, so app-store reach does not matter.

## Decision

Build a native Android app in Kotlin with Jetpack Compose (Material 3) for UI
and AndroidX Media3 for playback. Minimum SDK 26, target/compile SDK 36.

## Alternatives considered

- **Progressive web app.** One codebase for any device, but browser speech
  synthesis is unreliable when the screen is off and has no lock-screen
  controls; reliable background audio would require server-side speech
  generation, which conflicts with [ADR 0002](0002-free-offline-voices-only.md).
- **Flutter or React Native.** One codebase for Android and iOS, but background
  speech and media sessions depend on plugins of varying quality, and iOS
  sideloading without a paid developer account expires every 7 days. No iOS
  user exists today.

## Consequences

- Positive: first-class background playback, media session, audio focus and
  system TTS integration; mature tooling and libraries.
- Negative: Android only; an iOS version would be a separate effort.
- Follow-ups: keep platform-independent logic in `:textkit`
  ([ADR 0004](0004-pure-jvm-textkit-module.md)) to ease any future port.
