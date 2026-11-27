# Architecture decision records

Each file records one decision: its context, the decision, the alternatives
considered and the consequences. Accepted records are never rewritten; a new
record supersedes an old one, and the old one is marked
"Superseded by ADR NNNN". Copy [0000-template.md](0000-template.md) to add one.

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-native-android-kotlin-compose.md) | Native Android app with Kotlin and Jetpack Compose | Accepted |
| [0002](0002-free-offline-voices-only.md) | Free, on-device voices only | Accepted |
| [0003](0003-sentence-audio-rendering-with-media3.md) | Render sentences to audio and play them with Media3 | Accepted |
| [0004](0004-pure-jvm-textkit-module.md) | Pure Kotlin/JVM `textkit` module for text logic | Accepted |
| [0005](0005-pdf-extraction-pdfbox-and-mlkit-ocr.md) | PDF text via PdfBox, scanned pages via ML Kit OCR | Accepted |
| [0006](0006-reading-position-as-chapter-and-char-offset.md) | Reading position as chapter index and character offset | Accepted |
| [0007](0007-distribution-via-adb.md) | Distribute release builds via ADB | Accepted |
| [0008](0008-commit-directly-to-main.md) | Commit directly to `main` | Accepted |
| [0009](0009-english-code-and-portuguese-translation.md) | English code and docs, Portuguese translation resources | Accepted |
| [0010](0010-own-brand-identity-and-identifiers.md) | Own brand identity and app identifiers | Accepted |
| [0011](0011-ai-first-documentation.md) | AI-first documentation | Accepted |
| [0012](0012-no-accounts-local-only-data.md) | No accounts, local-only data | Accepted; how backup is turned off refined by 0021 |
| [0013](0013-layered-secret-protection.md) | Layered secret protection | Accepted |
| [0014](0014-target-android-17.md) | Target Android 17 (API 37) and test on it first | Accepted |
| [0015](0015-on-device-ocr-with-bundled-ml-kit.md) | On-device OCR with bundled ML Kit, resumable per page, in a dataSync foreground job | Accepted |
| [0016](0016-in-process-listening-state.md) | Share the spoken segment in-process; reader commands through the session | Accepted |
| [0017](0017-free-catalogue-from-project-gutenberg.md) | Free catalogue from Project Gutenberg through an allowlisted HTTPS client | Accepted; allowlist replaced by 0019, download retries by 0020 |
| [0018](0018-offline-neural-voices.md) | Offline neural voices with sherpa-onnx; GPL-3.0 applies to the APK | Accepted |
| [0019](0019-gutenberg-site-and-mirror-only.md) | Allowlist exactly Gutenberg's site and its official mirror; download from the mirror when the site is down | Accepted |
| [0020](0020-bounded-catalogue-downloads.md) | Bound catalogue downloads to about two minutes, and show and cancel them | Accepted |
| [0021](0021-leave-the-app-out-of-android-backup.md) | Leave the app out of Android backup so a backup never stops playback | Accepted |
