# 0009. English code and docs, Portuguese translation resources

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The repository is public and maintained with AI agents, which work best with
English code and documentation. The primary user reads Portuguese; their
phone may be set to pt-BR or pt-PT.

## Decision

- Code, comments, KDoc, commit messages and documentation are in English.
- User-facing strings are Android resources: English defaults in
  `res/values/`, Portuguese in `res/values-pt/` (matches both pt-BR and
  pt-PT). Android Lint's missing-translation check is an error.
- No UI text is hard-coded in Kotlin.

## Alternatives considered

- **Portuguese-only resources as defaults** — fewer files, but mixes languages
  in the codebase and makes future localization harder.

## Consequences

- Positive: consistent codebase language, ready for other locales.
- Negative: every string is written twice.
