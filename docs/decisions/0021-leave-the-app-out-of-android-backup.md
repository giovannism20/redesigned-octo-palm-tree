# 0021. Leave the app out of Android backup so a backup never stops playback

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

[ADR 0012](0012-no-accounts-local-only-data.md) keeps all data on the phone:
cloud backup off, device-to-device transfer allowed. It was implemented with
`android:allowBackup="true"` plus backup rules that exclude every file from
cloud backup.

During the first device test (Android 17, One UI 9), a "Back up now" in the
phone's settings started a full backup of R Reader. Android ran it even though
the rules excluded everything, and to run the backup agent it stopped the
app's process: playback would have stopped. Android's scheduled backups run
while the phone is idle and charging, which is exactly when the primary user
listens before sleeping.

Google Play services skips apps that set `android:allowBackup="false"`. For
apps that target Android 12 or later, that setting turns cloud backup off but
leaves device-to-device transfer to the `dataExtractionRules`, although
Android's documentation notes that some manufacturers handle device transfer
differently.

## Decision

- Set `android:allowBackup="false"`. The app takes no part in cloud backup, so
  no backup ever stops its process.
- Keep `android:dataExtractionRules`: its `device-transfer` section still
  carries books, positions and settings to a new phone where the platform
  applies it, and its `cloud-backup` excludes remain as a second guard.
- Remove the pre-Android 12 `fullBackupContent` rules, which no longer apply.
- `MergedManifestTest` checks both attributes.

## Alternatives considered

- **Keep `allowBackup="true"` with everything excluded** — uploads nothing but
  still lets a backup stop playback.
- **A key-value backup agent that runs in the app's own process** — avoids the
  stop but adds code for data that must not leave the phone anyway.

## Consequences

- Positive: a backup, scheduled or manual, no longer interrupts listening.
- Negative: on phones whose manufacturer ties device transfer to
  `allowBackup`, moving to a new phone may not carry the library and positions;
  the books can be added again.
- Follow-ups: the device checklist runs a manual backup while a book plays.
