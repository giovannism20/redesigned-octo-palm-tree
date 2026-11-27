# 0006. Reading position as chapter index and character offset

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

Durations of spoken text depend on the voice and speed, and audio is rendered
on demand, so time is not a stable coordinate. Sentence indices change if
segmentation rules change.

## Decision

The single source of truth for "where am I" is
`ReadingPosition(chapterIndex, charOffset)` into the **normalized chapter
text**. Progress (%) and time left are derived estimates. Normalized text is
immutable after import; if normalization changes, existing books keep their
text unless a migration re-maps positions explicitly.

## Alternatives considered

- **Time-based positions** — unstable across voices and speeds.
- **Sentence index** — breaks when segmentation rules improve.

## Consequences

- Positive: positions survive voice, speed and segmentation changes; resume
  snaps to the start of the containing sentence.
- Negative: time values shown to the user are estimates; the speaking rate per
  voice must be measured and stored to keep them accurate.
