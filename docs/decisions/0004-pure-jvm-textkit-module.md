# 0004. Pure Kotlin/JVM `textkit` module for text logic

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The trickiest logic — EPUB parsing, cleaning text extracted from PDFs and OCR,
chapter detection, Portuguese sentence segmentation, speech normalization and
time estimates — does not need Android APIs. Android unit tests are slower and
instrumented tests need a device.

## Decision

Put that logic in `:textkit`, a pure Kotlin/JVM Gradle module with no Android
dependencies, tested with plain JVM unit tests. `:app` depends on `:textkit`
and provides Android adapters (PdfBox-Android, `PdfRenderer`, ML Kit) that
produce plain data (layout lines) for `:textkit` to process.

## Alternatives considered

- **Everything in `:app`** — simpler build, but slow tests and easy leakage of
  Android types into core logic.
- **Many feature modules** — better isolation at scale, but too much build
  overhead for a small project.

## Consequences

- Positive: fast, deterministic tests for core logic; clear boundary between
  platform code and domain logic; easier future ports.
- Negative: one more module to configure; adapters must convert Android-side
  results into `textkit` types.
