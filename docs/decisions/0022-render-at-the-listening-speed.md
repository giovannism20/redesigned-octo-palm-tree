# 0022. Render neural voices at the listening speed, and say when a voice cannot keep up

- **Status:** Accepted; the player's slow-voice notice replaced by [0023](0023-no-slow-voice-notice-in-the-player.md)
- **Date:** 2026-11-27

## Context

[ADR 0003](0003-sentence-audio-rendering-with-media3.md) renders each segment
to a WAV file at the voice's own pace and lets the player stretch it to the
listening speed. [ADR 0018](0018-offline-neural-voices.md) expected Kokoro to
be about real time on a phone and rendered it ahead while charging.

On the primary user's phone, Dora and Alex (Kokoro) paused every three or four
sentences at 1.25×, while the phone's voice did not. On a modest test phone
(Snapdragon 695), Kokoro took 1.4 s to render one second of speech at 1.0×:
listening at 1.25× needs 1.75 s of rendering per second heard, so the player
runs out of audio whatever the render-ahead window. More threads did not help;
the full-precision model was only about 20 % faster and is 350 MB. Generating
at 1.25× in the model saved about 20 % of the render time, for Kokoro and
Piper alike ([measurements](../design/voices-evaluation.md)).

## Decision

- **Render at the listening speed.** A voice that can speak faster or slower
  itself (`Voice.rendersAtSpeed`: the neural voices) renders each segment at
  the listening speed rounded to the player's steps of 0.25 (`RenderSpeeds`),
  passed to sherpa-onnx as its generation speed. The internal player then
  plays it at 1.0×. The phone's voices still render at 1.0× and are stretched.
- **Audio at another speed is still used.** Segments are cached per speed
  (`…@x125.wav`; 1.0× keeps its old name); when the listening speed changes,
  audio cached at another speed, the closest first, is reused and the player
  stretches only the difference (`SegmentSpeed`). Audio rendered ahead while
  charging therefore stays useful.
- **Measure, then say so.** Engines report how long the voice itself took
  (model loading left out); `RenderSpeedTracker` keeps a moving average per
  voice, saved beside the speaking rates. When the player runs out of audio
  while the listener listens and the voice's measured cost times the speed is
  above 0.9, the session names the voice (`ListeningSession.slowVoice`): the
  player explains the pauses and offers a faster voice, and the Voices tab
  marks the voices of that model as slow on this phone.
- Positions, durations, time left and speaking rates stay in time at 1.0× of
  the voice's own pace: a segment rendered at speed *s* lasts its file length
  times *s*.

## Alternatives considered

- **More threads, or the full-precision Kokoro model** — measured: no gain, or
  about 20 % for a download almost three times larger.
- **Hold playback until a large buffer is ready** — fewer but much longer
  pauses, and still pauses: a voice slower than listening falls behind
  whatever the buffer.
- **Switch to a faster voice by itself** — the listener chose the voice; the
  app explains and offers the change instead.

## Consequences

- Positive: about a fifth less rendering at 1.25×, no time-stretching of
  neural voices at the chosen speed, and a clear reason and a way out when a
  voice is too slow for the phone.
- Negative: a speed change leaves the audio queued at the old speed, stretched
  until new segments arrive; a book may be cached at several speeds (the
  300 MB cache limit still applies).
- Follow-ups: check on the primary user's phone whether Kokoro keeps up at
  1.25× now; the notice is shown only after a pause, never ahead of one.
