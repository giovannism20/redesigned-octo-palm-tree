# 0014. Target Android 17 (API 37) and test on it first

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

The primary user's phone is a current Samsung flagship that received
Android 17 (One UI 9) in October 2026. Android 17 hardens background audio for
**all** apps: playing audio, requesting audio focus or changing volume from
the background requires a visible activity or a non-short foreground service,
and audio focus requests otherwise fail with `AUDIOFOCUS_REQUEST_FAILED`.
Apps that **target** API 37 must additionally hold a foreground service with
while-in-use capabilities when they play in the background. Current AndroidX
libraries already require compiling against API 37.1+.

## Decision

- `targetSdk` is 37 and `compileSdk` is 37.2; `minSdk` stays 26 so older
  phones keep working.
- Background listening always runs inside the Media3 `MediaSessionService`
  started as a `mediaPlayback` foreground service while the user is in the app
  (play pressed), which satisfies the while-in-use rule; the service stays
  active through transient interruptions and stops when playback ends.
- The reference test environment is Android 17: the API 37 emulator image and
  the primary user's phone, with the platform's strict check enabled during
  playback tests: `adb shell cmd audio set-enable-hardening throw`.
- Android 16 and older remain supported and are spot-checked, not the default
  test target.

## Alternatives considered

- **Stay on targetSdk 36** — avoids the while-in-use rule, but the hardening
  that applies to all apps would still hit the primary user's phone, and the
  app would lag the platform it actually runs on.

## Consequences

- Positive: tested against the OS the primary user really has; background
  playback designed for the strictest current rules.
- Negative: any future background audio path (for example sleep-timer fade or
  sample playback) must happen inside the foreground service or a visible
  activity.
- Follow-ups: playback tests include the hardening check (docs/testing.md).
