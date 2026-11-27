# Testing

## Strategy

| Level | Where | What it covers | Command |
|-------|-------|----------------|---------|
| Pure unit tests | `textkit/src/test` | Parsing, cleanup, segmentation, normalization, estimates | `./gradlew :textkit:test` |
| App unit tests | `app/src/test` | ViewModels, repositories, mapping, player logic with fakes | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests | `app/src/androidTest` | Room, import from real URIs, PDF cover and OCR page rendering (`PdfRenderer`), ML Kit text recognition of generated scans, `SystemTtsEngine`, playback service through `MediaController` | `./gradlew :app:connectedDebugAndroidTest` |
| Compose UI tests | `app/src/androidTest` | Screens, navigation, accessibility checks | same as above |
| Static analysis | all modules | Android Lint, detekt, Spotless/ktlint | `./gradlew check` |
| Security tests | `textkit` + `app` | Malicious EPUB/PDF inputs, secret guard | see below |
| Manual device checklist | real phone | End-to-end behaviour that emulators fake poorly | see below |

Frameworks: `:textkit` uses JUnit Jupiter 6 with Truth; `:app` uses JUnit 4
(the Android default) with Truth, AndroidX Test and the Compose test APIs.

The facade player (`ReaderPlayer`) is tested under Robolectric with a real
internal ExoPlayer on Media3's `FakeClock` (`media3-test-utils-robolectric`,
`TestPlayerRunHelper`) and a fake speech engine that writes small silent WAV
files, so rendering, playlists, seeks, focus changes and the end of the book
run for real in milliseconds. Audio focus changes are delivered through
Robolectric's `ShadowAudioManager`. The facade itself gets a separate,
manually advanced `FakeClock` (`ReaderPlayerFixture`): sleep timers and long
pauses then run deterministically while the audio plays on, and the timer
logic is also tested on its own (`SleepTimerTest`). Player screens also run under
Robolectric with `createComposeRule` (on a phone-sized screen) to check
spoken labels, touch targets of at least 56 dp and the actions they send.

Text recognition of scanned PDFs is tested on the JVM with a fake
`PageTextRecognizer` that returns what a recognizer would read on each page
(built through textkit's `OcrPageLayout` from pixel boxes at 300 dpi), so
scanned and mixed books, stored pages and resuming, limits, progress,
cancellation and removal run in milliseconds; test PDFs draw a small raster
image on their scanned pages. The real renderer and ML Kit are tested on a
device with scans drawn by the test itself (`PdfPageRasterizerTest`,
`MlKitPageTextRecognizerTest`).

The neural voices are tested without native code: `SherpaOnnxEngine` runs
over a fake `NeuralRuntime` (phonemes from characters, a tone per phoneme), so
speaker ids, Kokoro's merged phonemes, the dialogue pause, model loading and
replacement, typed failures and cancellation are checked on the JVM, and
`NeuralAudio`, `KokoroPhonemes` and `NeuralText` are plain unit tests. Voice
package downloads use the fake `HttpTransport` and archives built in the tests
(`TestArchives`): pinned SHA-256 and size, resuming with HTTP ranges, hosts off
the allowlist, free space, and hostile archives (traversal and absolute names,
links, wrong sizes, too many entries or bytes). On a phone, the instrumented
`SherpaOnnxEngineTest` speaks a sentence with a real Piper voice, downloading
Faber (67 MB) when no Piper voice is installed.

The catalogue is tested without the network: a fake `HttpTransport` answers
from a script (redirects, statuses, media types, broken, truncated and
oversized bodies), the feed parser and the license stripper run on feeds and
books written in the tests, and the download job runs with WorkManager's test
helpers and a real intake on an in-memory database. The Explore screen runs
under Robolectric like the player screens.

`./gradlew check` is the local gate and runs unit tests, lint, detekt and
formatting checks. CI runs the same plus the secret scan, CodeQL and the
documentation link check.

## Fixtures

- Generate EPUB and PDF fixtures **in test code** (small builders) instead of
  committing binary files.
- Use only synthetic text or public-domain text. Never commit copyrighted
  books or personal files.
- Keep fixtures minimal: one behaviour per fixture.

## Security tests

- **Secret guard:** `scripts/test-secret-guard.sh` must pass (also in CI).
- **Malicious EPUBs** (built in tests): path traversal entry names
  (`../../x`), zip bomb (huge declared/actual sizes, too many entries),
  XML with external entities or DTDs, missing/garbled container or OPF,
  metadata of megabytes, tables of contents hundreds of levels deep,
  millions of asterisks around Gutenberg's markers, a clock that runs past
  the parse time limit and an interrupted parse. Expected: a typed failure
  and a friendly message, or bounded processing time; never a crash, never a
  file written outside the book directory. Tests of time use a generous
  limit (`assertTimeoutPreemptively`) that only a hang exceeds.
- **Corrupted and huge PDFs** (built in test code with PdfBox-Android,
  `PdfSecurityTest`): truncated, garbage and password-protected files,
  documents over the page, time, glyph and buffer limits, a looping outline
  and a page that cannot be decoded. Expected: a typed failure or bounded
  processing time and memory.
- **Scans over the text recognition limits** (`PdfOcrTest`,
  `OcrLimitsTest`, `OcrPageStoreTest`): too many pages to recognize, the
  time budget across interrupted runs, huge page sizes, a failing or
  crashing recognizer, and stored pages that are damaged, too large or of
  another format. Expected: a typed failure, a capped bitmap, or the page
  recognized again.
- **Crashes in native code** (`NativeCallMarkersTest`, `PdfOcrTest`,
  `PdfBookParserTest`, `ImportBookWorkerTest`): a fake renderer or
  recognizer "kills the process" with an `Error` nothing catches, and the
  test puts back the native call marker the run left, as a real crash
  would. Expected: the call is made again after one death and skipped after
  two (cover left out, page without text, or "too complex"), and an import
  started ten times is given up.
- **Hostile network answers** (`CatalogHttpTest`, `CatalogHostsTest`,
  `GutenbergFeedParserTest`, `CatalogDownloaderTest`): addresses and redirects
  off the allowlist or to cleartext, too many redirects, unexpected media
  types, bodies over the limit (announced or not), broken and truncated
  connections, an HTML page served as an EPUB, XXE and entity expansion in
  feeds. Expected: a typed failure, and no request off the allowlist.

## Stress tests

- Import a 1,000-page PDF and an EPUB with 2 million characters; record time
  and peak memory.
- Hammer ±15 s and chapter jumps rapidly; the position must stay consistent.
- Play for 2 hours with the screen locked; check battery drain and memory.

## Test devices

- **Reference:** Android 17 (API 37) — the primary user's phone and the
  `rreader-api37` emulator (Pixel profile, Google Play image, so Google's TTS
  voices are available). See [ADR 0014](decisions/0014-target-android-17.md).
- **Compatibility spot checks:** Android 16 (`rreader-api36` emulator) and the
  minimum supported version when touching platform APIs.
- The emulator needs hardware virtualization (KVM). On AMD machines enable
  "SVM Mode" in the firmware settings if `/dev/kvm` is missing.
- During playback tests, enable Android 17's strict background-audio check:
  `adb shell cmd audio set-enable-hardening throw` (restore with `disable`).

## Manual device checklist

Run on a real phone (ideally the primary user's model) before installing a
new version:

1. Share a PDF and an EPUB from a messaging app to R Reader.
2. Import completes; the book appears with cover and title; tap play.
3. Lock the screen for 30 minutes; playback continues.
4. Use play/pause and next/previous on a Bluetooth headset.
5. Receive a phone call: playback pauses, then resumes after the call.
6. Force-close the app and reopen it: playback resumes at the same sentence.
7. Turn on airplane mode: playback continues.
8. Increase the system font size to the maximum: screens stay usable.
9. Turn on TalkBack: every control is announced.
10. Share a scanned PDF of about 100 pages: the library shows "Reconhecendo
    texto: página X de Y" and, if notifications are allowed, so does a
    notification; lock the screen until it finishes, then listen to a
    chapter.
11. Force-close the app while a scanned PDF is being recognized and reopen
    it: recognition continues from where it stopped, not from page 1.
12. With notifications denied for R Reader, import a scanned PDF: it still
    finishes. Remove a book while it is being recognized: the work stops.
13. In Explore, on Wi-Fi, download a suggested classic: it appears in the
    library, is prepared, and listening starts with the book itself, not
    with English license text. Download it again: it is already in the
    library.
14. Search "machado", tap "Mais resultados", then "Voltar às sugestões".
15. In airplane mode, Explore says the phone is offline and a search says
    there is no connection; "Baixar" waits, and the download starts by
    itself once airplane mode is off.
16. Start a download and lock the phone or switch apps: it finishes.
17. While a download runs, the book shows "Baixando…" (or "Tentando o site
    alternativo…" when Gutenberg's site is down) and "Cancelar", which stops
    it. A download that cannot finish says why within about two minutes and
    offers "Tentar de novo".
18. While a book plays, start a backup in the phone's settings (or run
    `adb shell bmgr backupnow --all`): playback goes on, and R Reader is not
    in the list of apps being backed up (ADR 0021).
