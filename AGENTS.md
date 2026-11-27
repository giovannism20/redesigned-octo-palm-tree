# AGENTS.md — start here

This file is the entry point for any AI agent (or person) working on this
repository. Read it fully before changing anything. `CLAUDE.md` only imports
this file, so every tool sees the same rules.

## What this project is

**R Reader** is an offline-first Android app that reads PDF and EPUB books
aloud with free, on-device voices. Its primary user is an older adult who
listens to long books in Portuguese on an Android phone, so simplicity,
large touch targets and reliable background playback matter more than
feature count. It is a personal open-source project (MIT).

## Hard rules (never break these)

1. **Language.** Code, comments, KDoc, commit messages and docs are in
   English. User-facing strings live in resources: English in
   `res/values/`, Portuguese in `res/values-pt/` (covers pt-BR and pt-PT).
   Never hard-code UI text.
2. **Free voices only.** No paid or cloud text-to-speech service. Voices are
   the Android system engine today and on-device neural models (sherpa-onnx)
   later. See [ADR 0002](docs/decisions/0002-free-offline-voices-only.md).
3. **Local-only data.** No accounts, no backend, no analytics or telemetry,
   no cloud backup. The network is used only when the user explicitly asks
   (download a public-domain book or a voice model), over HTTPS. Changing
   this needs a new ADR. See [ADR 0012](docs/decisions/0012-no-accounts-local-only-data.md).
4. **No third-party app branding.** Never reference, copy or imitate other
   consumer apps or their branding (names, logos, screenshots, copy,
   distinctive layouts) anywhere in the repo, including commit messages, and
   never commit reference screenshots. Naming the technologies, libraries and
   services this project uses or evaluated is fine.
   See [ADR 0010](docs/decisions/0010-own-brand-identity-and-identifiers.md).
5. **Secrets never enter git.** Keys go in `secrets.properties` (git-ignored)
   or GitHub Secrets. Never bypass the pre-commit hook (`--no-verify` is
   forbidden). If a secret leaks, follow
   [docs/runbooks/secrets.md](docs/runbooks/secrets.md): rotate first.
6. **No personal data** in code, docs, fixtures or commits. The repo is public.
7. **Workflow.** Commit directly to `main` with small
   [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`,
   `fix:`, `test:`, `docs:`, `build:`, `ci:`, `chore:`, `refactor:`).
   No issue or PR ceremony. CI must stay green; a red `main` is fixed before
   anything else. See [ADR 0008](docs/decisions/0008-commit-directly-to-main.md).

## Read in this order

1. [docs/roadmap.md](docs/roadmap.md) — what is done and what comes next
   (the resume point for any session).
2. [docs/product.md](docs/product.md) — user, goals, non-goals, UX principles.
3. [docs/architecture.md](docs/architecture.md) — modules, layers, data flow,
   invariants.
4. [docs/domain.md](docs/domain.md) — glossary of the names used in code.
5. [docs/decisions/](docs/decisions/README.md) — why things are the way they are.
6. [docs/testing.md](docs/testing.md) and [docs/security.md](docs/security.md).
7. [docs/runbooks/](docs/runbooks/) — step-by-step operational tasks.

## Repository map

| Path | What lives there |
|------|------------------|
| `app/` | Android application (Compose UI, playback service, import, TTS, data). |
| `textkit/` | Pure Kotlin/JVM library: book model, EPUB parsing, text cleanup, sentence segmentation, estimates. No Android dependencies. |
| `docs/` | Product, architecture, domain, roadmap, testing, security, ADRs, runbooks, design. |
| `.githooks/` | Versioned git hooks (pre-commit secret guard). |
| `scripts/` | Repository scripts (for example the secret-guard self-test). |
| `.github/` | CI workflows and Dependabot configuration. |
| `.claude/agents/` | Specialist agent definitions for AI-assisted work (see below). |
| `.claude/skills/` | Short task recipes for agents: build-and-install, new-adr, release. |
| `config/` | Static analysis configuration (detekt). |
| `gradle/` | Version catalog (`libs.versions.toml`), wrapper and daemon JDK criteria. |

The module and package layout is described in
[docs/architecture.md](docs/architecture.md). Update that file when it changes.

## Setup and commands

One-time setup on a new machine:

```bash
git config core.hooksPath .githooks          # the Gradle build also sets this
GOBIN=$HOME/.local/bin go install github.com/zricethezav/gitleaks/v8@latest
scripts/test-secret-guard.sh                  # proves the hook blocks secrets
```

Everyday commands. Gradle provisions its own JDK 21 (see
`gradle/gradle-daemon-jvm.properties`), so any Java 17+ runtime can start the
wrapper; the Android SDK must be installed (`local.properties` or `ANDROID_HOME`).

| Task | Command |
|------|---------|
| Full local gate (tests, lint, detekt, formatting) | `./gradlew check` |
| Fast pure-Kotlin tests | `./gradlew :textkit:test` |
| App unit tests | `./gradlew :app:testDebugUnitTest` |
| Format code | `./gradlew spotlessApply` |
| Build a debug APK | `./gradlew :app:assembleDebug` |
| Install on a connected phone | `./gradlew :app:installDebug` |
| Instrumented tests on a phone | `./gradlew :app:connectedDebugAndroidTest` |
| Release APK (needs signing setup) | `./gradlew :app:assembleRelease` |

Gradle builds are memory-hungry: do not run several heavy builds or
emulators in parallel on the same machine.

## Coding conventions

- **Layers:** Compose UI → `ViewModel` exposing immutable UI state through
  `StateFlow` → repositories → data sources (Room, files, TTS engines).
  No business logic inside composables; composables render state and send
  events.
- **Pure logic goes to `:textkit`** whenever it does not need Android APIs,
  so it stays fast to test.
- **Dependency injection** with Hilt. Constructor injection everywhere;
  no service locators.
- **Coroutines** with structured concurrency. Inject dispatchers; never use
  `GlobalScope`. Long work (imports, OCR, pre-rendering) runs in WorkManager.
- **Errors** are explicit: sealed result/error types at boundaries, mapped to
  string resources for the user. Never swallow exceptions; never show stack
  traces to the user.
- **Untrusted input** (imported files, downloads, intents) is validated at the
  boundary. Follow the security requirements in
  [docs/security.md](docs/security.md).
- **Accessibility:** touch targets ≥ 56 dp for primary controls (≥ 48 dp
  minimum), body text ≥ 18 sp, honour the system font scale, content
  descriptions on every icon-only control, contrast ≥ 4.5:1.
- **Logging:** no book content, file names or personal data in release logs.
- **Style:** Kotlin official style, enforced by Spotless + ktlint and detekt.
  Small classes and functions, intention-revealing names, no duplication,
  KDoc on public APIs of `:textkit`.
- **Tests:** every new piece of logic comes with tests. See
  [docs/testing.md](docs/testing.md).
- **Identifiers:** `applicationId` is `com.giovanni_martins.rreader`
  (permanent); the Kotlin namespace/package is `com.giovannimartins.rreader`
  (no underscore, per Kotlin conventions).

## Definition of done

A change is done when all of these are true:

- code and tests are committed, and `./gradlew check` passes locally;
- CI is green on `main`;
- docs are updated in the same commit (see below);
- for user-facing changes, the device checklist in
  [docs/testing.md](docs/testing.md) was run on a real phone.

## Keeping the context alive

Future agents rely on these files instead of chat history:

- A non-trivial decision gets a new ADR in `docs/decisions/` in the same
  commit. Accepted ADRs are never rewritten; supersede them with a new one
  and mark the old one "Superseded by".
- A change to modules, layers or flows updates `docs/architecture.md`.
- A new domain term updates `docs/domain.md`.
- Completed work is ticked in `docs/roadmap.md`; user-visible changes go in
  `CHANGELOG.md`.
- A changed public API updates the module README and KDoc.
- CI checks documentation links, so keep them valid.

## Working with AI agents

- **Resuming work:** read `docs/roadmap.md`, run `git log --oneline -15`, and
  continue from the first unchecked item. Tick items in the commit that
  completes them.
- **Parallel work:** independent roadmap items may run in separate git
  worktrees; integrate into `main` by rebasing locally, keeping commits small.
- **Project agents** in `.claude/agents/` (third-party, MIT — see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)):

| Agent | Use it for |
|-------|-----------|
| Mobile App Builder | Android/Kotlin/Compose implementation work. |
| Code Reviewer | Correctness and maintainability review before committing larger changes. |
| Brand Guardian | Logo, palette, typography and brand consistency. |
| UI Designer | Design system tokens, components, screen specs. |
| UX Researcher | Usability test scripts and findings for the primary user. |
| UI Finish-Gate Reviewer | PASS/HOLD review of screens before a release to the phone. |
| Security Architect | Threat model updates when the architecture changes. |
| Application Security Engineer | Security review at the end of each roadmap phase. |
| Secrets & Credential Hygiene Engineer | Changes to the secret guard, keys or signing. |
| AI-Generated Code Security Auditor | Audit of AI-written code before each release to the phone. |

Agents must follow this file; where an agent definition conflicts with these
rules, these rules win.
