# Roadmap

The single source of truth for progress. Any session resumes from the first
unchecked item. Tick items in the commit that completes them; add new items
when scope is discovered. Every phase ends with the app installed on the
primary user's phone (see [install-on-device](runbooks/install-on-device.md)).

## Phase 0 — Foundation and feasibility

- [x] Secret guard: `.gitignore`, pre-commit hook with gitleaks, self-test, CI full-history scan
- [x] License (MIT) and security policy
- [x] AI-first docs: `AGENTS.md`, product, architecture, domain, roadmap, testing, security, ADRs 0001–0013, runbooks
- [x] Project agents vendored in `.claude/agents/` with third-party notices
- [x] Gradle project: wrapper, version catalog, `:app` + `:textkit`, Compose theme placeholder, Hilt
- [x] Quality gates: Spotless + ktlint, detekt, Android Lint, hooks installed by Gradle
- [x] `secrets.properties` pattern wired into Gradle (`BuildConfig`), release signing from properties outside the repo
- [ ] CI: build, unit tests, lint, detekt, formatting, Gradle wrapper validation, CodeQL, documentation link check
- [x] Enable private vulnerability reporting on GitHub
- [x] Emulator tooling: command-line tools, emulator, Android 16/17 Play images, `rreader-api36` AVD (needs KVM: enable SVM in firmware)
- [x] `rreader-api37` AVD (Android 17 reference image)
- [ ] Feasibility spike on a real phone: `synthesizeToFile` speed/quality with the Google pt-BR voice
- [ ] Feasibility spike on a real phone: sequential sentence playback for 30+ minutes with the screen locked

## Phase 0b — Visual identity

- [x] 3–4 logo concepts ("an R overlapping another R") as SVG, shown on a comparison page (awaiting the owner's choice)
- [ ] Chosen logo → adaptive icon (foreground/background), monochrome themed icon, splash screen, in-app logo
- [ ] Palette, large type scale and core components → Compose theme (`docs/design/`)
- [x] Short usability test script for the primary user ([docs/design/usability-test.md](design/usability-test.md))

## Phase 1 — EPUB MVP (first version on the phone)

- [x] `textkit`: book model, EPUB 2/3 parser (container, OPF, spine, nav/NCX, cover, DRM detection), safe ZIP/XML handling
- [x] `textkit`: sentence segmentation (pt-BR abbreviations), speech normalization, estimates
- [x] Import: picker + share/view intents, signature detection, copy + SHA-256 dedupe, WorkManager job, error messages
- [x] Data: Room schema, chapter files, library repository
- [x] Data: settings (DataStore: default voice, speed, text size)
- [x] Library screen with "Continue listening", progress, failures and removal
- [x] Library: tap a book to play it and open the player
- [x] Library and player: time left from `ReadingEstimates` and persisted speaking rates
- [x] Wire the EPUB parser into `ImporterModule`
- [ ] EPUB: split chapters at TOC fragment anchors inside a single file (books shipped as one big XHTML)
- [x] `SystemTtsEngine` (multi-engine, offline-first voices) + audio cache
- [x] `SegmentRenderer` (render-ahead window, speaking-rate updates)
- [x] `PlaybackService` with facade player: background, lock screen, headset, ±15 s, speed, chapters, resume
- [x] Mini-player and full player screens
- [x] Voices screen (installed pt voices, sample, default voice)
- [ ] Device checklist passed; installed on the primary user's phone

## Phase 2 — PDF with a text layer

- [x] PdfBox extraction to layout lines
- [x] Cleanup: headers/footers, page numbers, hyphenation with enclisis, paragraphs
- [x] Chapters from outline → headings → page ranges; cover from page 1
- [x] Scanned-PDF detection with a clear message (until Phase 4)
- [ ] PDF: two-column layouts and footnotes (read inline today)
- [ ] Unify the two internal `RomanNumerals` helpers in `:textkit`

## Phase 3 — Comfort

- [x] Follow-along reader: highlighted sentence, auto-scroll, tap to read from here, text size
- [x] Sleep timer (minutes or end of chapter, fade-out)
- [x] Small automatic rewind when resuming after a long pause
- [x] Fix the latent audio-focus race in `ReaderPlayerTest`'s phone-call test (wait for the focus request)

## Phase 4 — Scanned PDFs (OCR)

- [ ] ML Kit OCR per page, resumable, with progress
- [ ] Mixed PDFs (pages with and without text)

## Phase 5 — Offline neural voices

- [ ] Voice comparison samples (Kokoro, Piper, system voice) for the user to choose
- [ ] `SherpaOnnxEngine`, model download over Wi-Fi with size shown and SHA-256 verification
- [ ] Pre-rendering while charging when a voice is slower than real time

## Phase 6 — Free catalogue

- [ ] Explore tab: curated public-domain classics in Portuguese + search
- [ ] Download into the library; strip license boilerplate from the spoken text

## Release (every phase)

- [ ] Release signing set up outside the repo, keystore backed up
- [ ] Release APK installed via ADB on the primary user's phone
