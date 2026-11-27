# 0016. Share the spoken segment in-process; reader commands through the session

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

The follow-along reader must highlight the segment being spoken and let the
user tap a sentence to read from there; the player shows the sleep-timer
countdown. That state changes several times a minute and lives in the
playback service, while the UI talks to playback only through a Media3
`MediaController` (ADR 0003).

## Decision

- **State out:** `ListeningStatus`, a Hilt singleton with two `StateFlow`s (the
  spoken segment — book, chapter, character range — and the sleep timer).
  `ReaderPlayer` publishes to it; the UI only reads it. This relies on the
  playback service running in the app's own process.
- **Commands in:** reader actions stay on the session as custom commands
  (`ReaderCommands`: read from a position, set or cancel the sleep timer).
  `ReaderSessionCallback` grants them only to controllers running as the
  app's own uid and validates their arguments (SR-7).
- The sleep timer and its fade run inside the player in the foreground
  service, as Android 17 requires for background audio changes (ADR 0014).

## Alternatives considered

- **Session extras / custom metadata for the segment** — works across
  processes, but adds serialization and update traffic for no benefit while
  the service is in-process.
- **A bound service with its own API** — duplicates what the media session
  already provides.

## Consequences

- Positive: the reader stays in sync without polling; commands keep a single,
  validated entry point.
- Negative: moving the playback service to another process would require
  carrying the segment through session extras instead.
