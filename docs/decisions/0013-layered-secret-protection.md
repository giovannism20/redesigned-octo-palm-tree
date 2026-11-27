# 0013. Layered secret protection

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The repository is open source and may one day need an API key. A secret that
reaches a public repository must be considered compromised immediately; no
single control is reliable on its own.

## Decision

Defend in layers, all in place before any application code:

1. `.gitignore` for secrets, keystores, env files and release binaries.
2. Keys only in `secrets.properties` (git-ignored), read by Gradle into
   `BuildConfig`; `secrets.defaults.properties` holds placeholders; CI uses
   GitHub Secrets.
3. A versioned pre-commit hook (`.githooks/pre-commit`, installed by the
   Gradle build via `core.hooksPath`) that blocks secret-bearing files even
   when force-added and runs gitleaks on the staged diff.
4. CI scans the full history with gitleaks on every push and runs the hook's
   self-test.
5. GitHub secret scanning and push protection enabled.
6. Release keystore and passwords outside the repository.
7. Commits use the GitHub no-reply e-mail to avoid exposing a personal
   address.

Anything compiled into the APK can be extracted, so a genuinely sensitive key
would need a server-side proxy, which is out of scope today.

## Alternatives considered

- **Only `.gitignore`** — does not stop force-adds or secrets pasted in code.
- **Only GitHub push protection** — catches known provider patterns, not
  generic secrets, and only at push time.

## Consequences

- Positive: a leak needs several independent failures.
- Negative: contributors must install gitleaks; the hook fails closed without
  it.
- Follow-ups: leak response in [runbooks/secrets.md](../runbooks/secrets.md).
