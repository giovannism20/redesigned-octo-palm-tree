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
logic is also tested on its own (`SleepTimerTest`). The listening session the
player publishes, and the pre-render it asks for only while nobody listens, are
checked with a recording fake (`ReaderPlayerSessionTest`); the pre-render job
itself runs with WorkManager's test helpers (`PrerenderTest`: held back while
listening or while another model is in use, stopped after the segment being
rendered, the same book and voice keep their job, a job started three times is
given up). In `SegmentRendererTest` the renderer's background renders wait
for the player's window, and a voice with a new render version renders its
cached segments again. Player screens also run under
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
speaker ids, Kokoro's merged phonemes, the dialogue pauses (also for a dash
that opens a new line of dialogue inside a segment), model loading and
replacement, typed failures (also for `LinkageError` and `OutOfMemoryError`),
cancellation, a package deleted while a render runs and another waits, and
releasing the model after two idle minutes (on a `TestScope` clock) or when
the system asks for memory are checked on the JVM, and
`NeuralAudio`, `KokoroPhonemes` and `NeuralText` are plain unit tests. Voice
package downloads use the fake `HttpTransport` and archives built in the tests
(`TestArchives`): pinned SHA-256 and size, resuming with HTTP ranges (a corrupt
start is refused as damaged), partial archives named after their pin, what a
download left behind, hosts off the allowlist, free space, and hostile archives
(traversal and absolute names, symbolic and hard links, devices and named
pipes at a kept name, wrong sizes, too many entries or bytes), and every kept
file is flushed to the disk before its folder is put in place.
`VoicePackageArchiveTest` stays a plain JUnit test: Robolectric's Android jar
carries an older Commons Compress, which Robolectric tests load instead of the
app's. The download jobs run with WorkManager's test helpers, whose background
tasks a test can hold, as a busy phone would, to check that cancelling waits for
WorkManager before it deletes what a download received; a job that really fails
shows how a failed download is removed, and how the files of downloads that
neither wait nor run are deleted when the app opens. The worker runs with any
attempt count, to check that only attempts in a row that receive nothing make
it give up. On a phone, the instrumented
`SherpaOnnxEngineTest` speaks a sentence with a real Piper voice, downloading
Faber (67 MB) when no Piper voice is installed, and `EspeakPhonemizerTest`
feeds espeak-ng text that makes it name letters, switch language or meet other
alphabets (such text crashed it in native code before the English, German,
French and Polish dictionaries shipped); the JVM test `EspeakDictionariesTest`
checks that every dictionary a shipped one switches to is shipped too, and
`EspeakDataTest` that the data is copied whole, and copied again when its copy
was never marked complete. Run instrumented tests on the owner's phone with
`adb install -r` and `adb shell am instrument`, not `connectedDebugAndroidTest`,
which uninstalls the app and deletes its books and voices.

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
- **Crashes in native code** (`NativeCallNoteTest`, `NativeCallMarkersTest`,
  `PdfOcrTest`, `PdfBookParserTest`, `ImportBookWorkerTest`, and for the
  neural voices the `NeuralCallMarkersTest` of `tts/neural`): a fake renderer,
  recognizer or neural runtime "kills the process" with an `Error` nothing
  catches, and the test puts back the native call marker the run left, as a
  real crash would; each new engine or run stands for a new process.
  Expected: the call is made again after one death and skipped after two
  (cover left out, page without text, or "too complex"; a neural segment
  becomes a pause that the audio cache keeps, a phonemizer or model that
  died starting makes the voice unavailable), an import started ten times
  is given up, and so is a pre-render job started three times
  (`PrerenderTest`).
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
19. In Vozes, every voice has its own emblem, name and description, and
    "Ouvir" plays its sample at once, also in airplane mode.
20. On Wi-Fi, download Cadu (67 MB): it shows "Baixando… N%", then "Usar
    esta voz"; choose it while a book plays: the voice changes at the
    sentence being read, without the book starting over.
21. On mobile data, "Baixar" asks first; "Esperar o Wi-Fi" waits and the
    download starts once the phone is on Wi-Fi. "Cancelar" stops a download.
    A download that ends with a message (on a connection that keeps
    failing, or without space) offers "Tentar de novo" and "Remover", which
    brings back "Baixar · 67 MB".
22. Download Dora (132 MB, also brings Alex and Santa) and listen while the
    phone charges: after a while the next chapters play without "Preparando a
    voz…" (rendered ahead).
23. In the player, "Voz" lists the ready voices; choose one "Só neste livro"
    and check another book still reads with the default voice.
24. Delete the voice in use: listening goes on with the phone's voice, and
    "Voz do celular" shows "Em uso".
25. "Sobre e licenças" opens from the bottom of Vozes, shows the version, the
    source address, the GPL note and every licence text; "Abrir no
    navegador" opens the repository.
