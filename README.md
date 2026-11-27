# R Reader

An offline-first Android app that reads your PDF and EPUB books aloud with
free, on-device voices.

> **Status:** in early development. See the [roadmap](docs/roadmap.md).

## What it does (version 1 goals)

- Import a PDF or EPUB from the file picker or by sharing it from another app.
- A library with covers, progress and estimated time left.
- A player that keeps reading with the screen locked, with lock-screen and
  headset controls, ±15 s skips, chapters and speed control.
- Follow-along text with the current sentence highlighted.
- A sleep timer.
- Scanned PDFs read through on-device text recognition.
- A catalogue of free public-domain classics.
- Natural offline voices to download once, with samples to compare them.

## Principles

- **Free and offline.** Voices run on the phone. No paid services.
- **Private by design.** No account, no server, no analytics. Your books and
  progress stay on your phone.
- **Easy to use.** Large text and buttons, simple flows, works for listeners
  who are not comfortable with technology.

## Building

Requirements: any Java 17+ runtime to start Gradle (the build provisions its
own JDK 21), the Android SDK, and `gitleaks` for the pre-commit secret guard.

```bash
git config core.hooksPath .githooks
./gradlew check                 # tests, lint, static analysis, formatting
./gradlew :app:installDebug     # install on a connected phone
```

More commands and the project conventions are in [AGENTS.md](AGENTS.md).

## Documentation

- [Product](docs/product.md) · [Architecture](docs/architecture.md) ·
  [Domain glossary](docs/domain.md) · [Decisions](docs/decisions/README.md)
- [Testing](docs/testing.md) · [Security](docs/security.md) ·
  [Runbooks](docs/runbooks/)

## License

The source code in this repository is [MIT](LICENSE). The app built with the
offline neural voices links a GPL-3.0 pronunciation library, so that APK is
distributed under GPL-3.0 terms ([ADR 0018](docs/decisions/0018-offline-neural-voices.md)).
The app shows the licence texts and the address of this repository in
Vozes → Sobre e licenças. Third-party notices are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
