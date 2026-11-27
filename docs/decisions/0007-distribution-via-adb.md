# 0007. Distribute release builds via ADB

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The app targets a few family devices in Brazil. Since 2026-09-30, certified
Android devices in Brazil block new installs of apps from developers who have
not completed Google's developer verification, except through ADB or an
advanced user flow. A Play Console account costs a one-time fee; a free
"limited distribution" account (up to 20 devices) exists for hobbyists.

## Decision

Install signed release APKs with `adb install -r`, over USB or wireless
debugging, following [runbooks/install-on-device.md](../runbooks/install-on-device.md).
Keep the same release signing key for every version so updates preserve data.

## Alternatives considered

- **Google Play (internal testing)** — automatic updates, but a paid account
  and store review overhead.
- **Limited distribution account** — free and allows remote updates; worth
  revisiting if physical access to the phone becomes a problem.
- **Sideloading an APK file** — blocked for unverified developers on
  certified devices in Brazil.

## Consequences

- Positive: free, always permitted, no store dependency.
- Negative: updates need physical or same-network access to the phone; on
  Samsung devices "Auto Blocker" must be turned off temporarily.
- Follow-ups: losing the signing key forces an uninstall (data loss), so the
  key must be backed up ([runbooks/release-signing.md](../runbooks/release-signing.md)).
