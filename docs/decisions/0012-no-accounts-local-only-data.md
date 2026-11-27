# 0012. No accounts, local-only data

- **Status:** Accepted; how backup is turned off refined by [0021](0021-leave-the-app-out-of-android-backup.md)
- **Date:** 2026-11-26

## Context

The primary user should not need to create an account to listen to a book,
and the project has no budget or desire to operate servers. Books and reading
habits are private.

## Decision

- No account or login, at least in version 1.
- All data (books, extracted text, positions, settings) stays in app-private
  storage on the phone.
- No backend, analytics, telemetry, ads or crash reporting services.
- Network access only after an explicit user action (catalogue or voice model
  download), over HTTPS.
- Android cloud backup is disabled; device-to-device transfer is allowed so a
  phone migration keeps progress.

## Alternatives considered

- **Cloud sync with an account** — convenient across devices, but needs a
  backend, credentials and a privacy policy.
- **Android cloud backup** — convenient, but copies reading history off the
  device.

## Consequences

- Positive: privacy by design, no running costs, works offline.
- Negative: losing the phone loses the library (books can be re-imported);
  no multi-device sync.
- Follow-ups: an explicit local export/import of the library could be added
  later without changing this decision.
