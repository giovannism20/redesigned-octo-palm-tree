# 0003. Render sentences to audio and play them with Media3

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

Speaking text directly with `TextToSpeech.speak()` gives no real timeline:
positions and durations are guesses, seeking is ad hoc, speed changes alter
the voice, and audio focus, "becoming noisy", notifications and lock-screen
controls must be hand-built. Future neural engines produce PCM audio, not
`speak()` calls, so a `speak()`-based player would need a second code path.

## Decision

Treat speech as audio production followed by ordinary media playback:

- A `SpeechEngine` renders a **segment** (one or more sentences) to an audio
  file. `SystemTtsEngine` uses `TextToSpeech.synthesizeToFile`;
  `SherpaOnnxEngine` will write PCM from on-device models.
- A `SegmentRenderer` keeps a window of upcoming segments rendered ahead and
  manages a size-capped audio cache (temporary WAV for fast engines, cached
  compressed audio for slow neural engines).
- ExoPlayer plays rendered segments as a playlist inside a
  `MediaSessionService` running as a `mediaPlayback` foreground service.
- A **facade player** (`ForwardingSimpleBasePlayer`/`SimpleBasePlayer`)
  presents one item per chapter with a continuous position and duration to
  the media session, and maps ±15 s, seeks and chapter changes to segment
  operations. Headset next/previous map to ±15 s.
- Playback speed is applied by the player (pitch preserved), not by the
  engine.

## Alternatives considered

- **Direct `TextToSpeech.speak()` with a custom player** — lower latency and
  no files, but estimated positions, manual focus/notification handling and a
  separate path for neural engines. Kept as a fallback if
  `synthesizeToFile` proves unreliable on a target phone.
- **Rendering whole chapters before playback** — exact timelines but long
  waits before the first word.

## Consequences

- Positive: one playback path for every engine; Media3 provides background,
  lock screen, headset, audio focus and speed handling; exact durations for
  rendered audio; natural pauses between sentences hide segment boundaries.
- Negative: a rendering pipeline and cache to maintain; first-play latency of
  one rendered segment; extra storage for cached audio.
- Follow-ups: validate `synthesizeToFile` speed and long screen-locked
  playback on a real phone (roadmap Phase 0 spikes).
