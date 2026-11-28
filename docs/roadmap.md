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
- [x] First real-device run (Galaxy S24+, Android 17 / One UI 9, 2026-11-27): instrumented tests for the playback service, PDF cover renderer and ML Kit OCR pass (two fixture-size failures fixed)
- [ ] Feasibility spike on a real phone: `synthesizeToFile` speed/quality with the Google pt-BR voice
- [ ] Feasibility spike on a real phone: sequential sentence playback for 30+ minutes with the screen locked

## Phase 0b — Visual identity

- [x] 3–4 logo concepts ("an R overlapping another R") as SVG, shown on a comparison page — owner chose "Livro Aberto"
- [x] Chosen logo → adaptive icon (foreground/background), monochrome themed icon, splash screen, in-app logo
- [x] Palette, large type scale and core components → Compose theme ([brand.md](design/brand.md))
- [x] Short usability test script for the primary user ([docs/design/usability-test.md](design/usability-test.md))
- [x] First device test with the owner (2026-11-27); findings and fixes in [usability-findings.md](design/usability-findings.md)
- [x] One design system on every screen (owner request 2026-11-27): every Material colour role mapped to the brand, one typeface for low vision (Atkinson Hyperlegible Next), full type scale, `ScreenSurface` for screens outside the tab scaffold, guarded by `ThemeTest`
- [ ] Design pass on the remaining screens with the UI Finish-Gate Reviewer: our own layouts (library item order, a book menu instead of an inline "Remove"), consistent headers and spacing

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
- [x] EPUB: split chapters at TOC fragment anchors and chapter headings inside large files (Dom Casmurro: 3 → 148 chapters)
- [x] `SystemTtsEngine` (multi-engine, offline-first voices) + audio cache
- [x] `SegmentRenderer` (render-ahead window, speaking-rate updates)
- [x] `PlaybackService` with facade player: background, lock screen, headset, ±15 s, speed, chapters, resume
- [x] Mini-player and full player screens
- [x] Full player fits one screen (cover shrinks first, scrolls only with very large text); swipe down to minimize
- [x] A backup run by the phone no longer stops playback: the app is out of Android backup ([ADR 0021](decisions/0021-leave-the-app-out-of-android-backup.md)); verified with `bmgr` on the phone
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

- [x] ML Kit OCR per page, resumable, with progress
- [x] Mixed PDFs (pages with and without text)
- [x] Release APK size: ABI splits (arm64-v8a for phones ≈ 21 MB, x86_64 for the emulator) instead of a ~52 MB universal APK
- [x] Keep ML Kit's telemetry upload backend removed when the INTERNET permission arrives with the catalogue

## Phase 5 — Offline neural voices

- [x] Voice comparison samples (Kokoro, Piper) for the user to choose ([evaluation](design/voices-evaluation.md))
- [x] Owner decisions: all nine voices, GPL-3.0 for the APK only ([ADR 0018](decisions/0018-offline-neural-voices.md))
- [x] Voice emblems (abstract marks) and short bundled samples playable before download (owner tried them on the phone)
- [ ] "About and licences" screen: licence texts (GPL-3.0, Apache-2.0, MIT, voice models) and a link to the source
- [x] `SherpaOnnxEngine`, model download over Wi-Fi with size shown and SHA-256 verification (all nine voices downloaded on the phone; Faber read all 5,228 sentences of Dom Casmurro without a crash after the two native fixes)
- [ ] Pre-rendering while charging when a voice is slower than real time (implemented; verify Kokoro's speed and the pre-render on the phone)
- [x] Review findings: crash containment for native calls (SR-14), pre-render out of the listener's way, last voice change wins, fallback to the phone voice, download hygiene, espeak-ng memory-safety note and input bounds; Kokoro's 4.8/9.6 kHz whistle removed (measured on the phone)
- [ ] On the phone: playback with the new session and pre-render rules, dialogue pauses with Kokoro and Piper, the fallback notice with TalkBack
- [x] Neural voices render at the listening speed; a voice too slow for the phone is explained in the player and marked in Vozes ([ADR 0022](decisions/0022-render-at-the-listening-speed.md)); verified on a modest test phone (Kokoro renders at 1.4× real time there)
- [ ] On the primary user's phone: Dora at 1.25× without pauses after ADR 0022, or the slow-voice notice
- [ ] Rebuild piper-phonemize against espeak-ng 1.52+ (memory-safety fixes) and pin the AAR

## Phase 6 — Free catalogue

- [x] Explore tab: curated public-domain classics in Portuguese + search
- [x] Download into the library; strip license boilerplate from the spoken text
- [x] Exactly Gutenberg's site and its official mirror; downloads fall back to the mirror when the site is down, bounded to about two minutes, with status, Cancel and Try again ([ADR 0019](decisions/0019-gutenberg-site-and-mirror-only.md), [ADR 0020](decisions/0020-bounded-catalogue-downloads.md)); verified on the phone while the site was down
- [ ] Verify the live Gutenberg search with `l.pt` once the site is back (it was down during development)
- [ ] Owner decision: optional contact address for the catalogue User-Agent (`CATALOG_CONTACT` in `secrets.properties`)

## Security review

- [x] Application security review of phases 0–6; its ten findings fixed (bounded metadata, trusted session callers, linear-time Gutenberg markers, EPUB parse budget, failed-import end states, native crash markers, intake errors, no book ids in release logs, catalogue title caps, exact hosts) — see [security.md](security.md)

## Phase 7 — Personal settings (owner request 2026-11-27)

- [x] Owner decisions: keep Atkinson Hyperlegible Next (its slashed zero included); settings for text size, light or dark theme and the reading font
- [x] "Ajustes" tab with a live preview; "Adicionar livro" moves into the Library so the bar keeps four tabs
- [x] Text size for the whole app, on top of the phone's font size (Normal, Grande, Maior, Muito grande; dialogs, sheets and menus too)
- [x] Light theme from the brand (paper, ink, sealing wax), dark by default, or the phone's choice; AA contrast checked for both
- [x] Reading font for the follow-along text: Atkinson Hyperlegible Next or a serif book face (Literata)
- [ ] On the phone: every screen in Claro and at Muito grande (with the phone's font at maximum too), the theme after a restart, Literata in the reader, sharing a book still works

## Release (every phase)

- [x] Release signing set up outside the repo (keystore created 2026-11-27)
- [ ] Owner backs up the keystore and its passwords
- [ ] Release APK installed via ADB on the primary user's phone
