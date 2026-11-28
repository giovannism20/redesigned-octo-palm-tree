# 0023. No slow-voice notice in the player

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR 0022](0022-render-at-the-listening-speed.md) added a sentence under the
player's controls when listening paused because the voice renders slower than
the listener hears on the phone. Tried on a modest phone, the owner found it
made no sense to the listener, and its several lines pushed the book's cover
off the player (the cover gives way when the screen is full).

## Decision

- The player shows no slow-voice notice. While the voice catches up, it shows
  "Preparando a voz…" as before.
- Everything else in ADR 0022 stays: neural voices render at the listening
  speed, the render cost is measured per voice on the phone, and the Voices
  tab marks the voices too slow there at the chosen speed.

## Alternatives considered

- **A shorter notice** — one sentence still took the cover's place on a
  common screen size, and the owner did not want the notice at all.

## Consequences

- Positive: the player keeps its cover and stays simple.
- Negative: in the player, a pause because of a slow voice looks like any wait
  for audio; the reason is only told in the Voices tab.
