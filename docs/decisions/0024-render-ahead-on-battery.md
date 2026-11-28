# 0024. Render slow voices ahead on battery too, in a foreground job

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR 0018](0018-offline-neural-voices.md) renders a book read by a slow voice
(Kokoro) ahead of listening, but only while the phone charges. On a modest
phone Kokoro renders at about 1.6× real time even at the listening speed
([ADR 0022](0022-render-at-the-listening-speed.md)), so listening pauses every
sentence or two unless audio was rendered before; the owner asked for that
preparation without waiting for the charger.

A WorkManager job outside a foreground service is stopped after about ten
minutes, and rendering an hour of Kokoro audio takes far longer on such a
phone. The import's text recognition already runs in a `dataSync` foreground
service for the same reason ([ADR 0015](0015-on-device-ocr-with-bundled-ml-kit.md)).

## Decision

- The pre-render job no longer requires charging. It still requires a battery
  that is not low and enough storage, and it still never runs while the book
  is listened to (the listening session holds it back).
- It runs in WorkManager's `dataSync` foreground service with a silent,
  low-importance notification ("Preparando a leitura", with how much of the
  hour ahead is ready, naming no book). When the platform refuses the
  foreground service (for example a job started from the background), it
  renders as an ordinary job.
- The amount rendered ahead stays at an hour of audio (within the 300 MB
  cache).

## Alternatives considered

- **Keep waiting for the charger** — on a slow phone a slow voice then pauses
  all day.
- **Render ahead while listening, in the playback service** — the listener's
  own renders already use every core; more work there would only slow them.
- **An ordinary job without a notification** — stopped after about ten
  minutes, and the battery use would be invisible.

## Consequences

- Positive: a book paused for a while plays without pauses afterwards, even
  away from the charger.
- Negative: rendering uses battery (Kokoro keeps four cores busy); the
  notification makes it visible, and the job stops when the battery gets low.
  Android limits `dataSync` foreground services to six hours a day.
