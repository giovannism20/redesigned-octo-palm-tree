# Security and privacy

Threat model and security requirements for R Reader. Update it when the
architecture changes (new input, new network call, new exported component,
new dependency class). Reporting policy: [SECURITY.md](../SECURITY.md).

## System overview

- **Architecture:** single Android app, no backend, no accounts.
- **Stack:** Kotlin, Jetpack Compose, Media3, Room, WorkManager, PdfBox-Android,
  ML Kit text recognition (on-device), Android TTS, sherpa-onnx with
  piper-phonemize (offline neural voices, ADR 0018).
- **Data handled:** books the user imports (may be copyrighted or personal),
  reading positions and settings. No credentials, no contact data, no
  location, no identifiers are collected.
- **Network:** only on explicit user action, over HTTPS — catalogue searches
  and public-domain book downloads from Project Gutenberg
  ([ADR 0017](decisions/0017-free-catalogue-from-project-gutenberg.md)), and
  voice package downloads from the official sherpa-onnx releases on GitHub
  ([ADR 0018](decisions/0018-offline-neural-voices.md)).
- **Distribution:** release APK signed by the maintainer, installed via ADB.

## Assets

| Asset | Sensitivity | Location |
|-------|-------------|----------|
| Imported books and extracted text | Private to the user | App-private storage |
| Reading positions, settings, per-book voice | Low (reveals reading habits) | Room / DataStore |
| Catalogue searches and downloaded book numbers | Low (reveal reading interests) | Search pages in memory for 15 minutes; download jobs in WorkManager; sent to Project Gutenberg |
| Downloaded voice packages | Integrity: their code-like models run on the phone | `filesDir/voices/{package}/`, verified by SHA-256 before use |
| App integrity (code that runs on the phone) | High | Signed APK |
| Release signing key | Critical (controls updates) | Maintainer machine, outside the repo, backed up |
| Repository and CI integrity | High | GitHub |

## Trust boundaries

| # | Boundary | From → To | Controls |
|---|----------|-----------|----------|
| B1 | File intake | Any file chosen/shared by the user → importer | Signature-based type detection, size limits, parsing limits, typed failures |
| B2 | Inter-app intents and media controllers | Other apps → exported activity / media session service | Minimal exported components, accept only `content://` URIs with read grants, validate MIME and content; the media session accepts only trusted controllers and book-id requests, ignores media buttons and notification actions that other apps send as start intents, and grants the app's own commands to the app's own controllers only |
| B3 | Network | Internet → catalogue client (search feeds, book downloads) and voice package downloader | Every request goes through `HttpsClient` with one host allowlist per kind of request: `CatalogHttp` with `CatalogHosts` (exactly `www.gutenberg.org` and its official mirror `gutenberg.pglaf.org`, [ADR 0019](decisions/0019-gutenberg-site-and-mirror-only.md)) and `VoiceModelHttp` with `VoiceModelHosts` (exactly `github.com`, `release-assets.githubusercontent.com` and `objects.githubusercontent.com`); every address and redirect checked, at most 5 redirects, expected media type, size limits, timeouts; the network security config disables cleartext and trusts no CA for other hosts; downloaded books then cross B1 like any file; voice packages must match their pinned size and SHA-256 (SR-6) before they are unpacked, keeping only expected files (SR-1, SR-2) |
| B4 | TTS engine | App → third-party TTS engine process | Prefer offline voices; flag voices that need the network (their text leaves the device) |
| B5 | Build supply chain | Dependencies, Gradle wrapper, Actions → build | Version catalog, Dependabot, wrapper validation, Actions pinned by SHA, CodeQL; the AARs published only on GitHub are downloaded by the build from their releases and refused unless they match pinned SHA-256 values |
| B6 | Public repository | Developer machines → GitHub | Secret guard layers, no personal data, reviewed third-party content |

## STRIDE analysis

| Threat | Component | Risk | Scenario | Mitigation |
|--------|-----------|------|----------|------------|
| Tampering / EoP | EPUB parser (B1) | High | ZIP entry `../../databases/x` overwrites app files (zip slip) | Never write files using archive entry names; read entries in memory by exact name |
| DoS | EPUB parser (B1) | Medium | Zip bomb or millions of entries exhaust memory/storage | Caps on entry count, per-entry (16 MiB) and total uncompressed size and on the text extracted (20 million characters); streaming reads |
| DoS | Import job (B1) | Medium | A file makes a parser run out of memory or stack, or throw an exception nobody expected, or makes the process die; the book stays "being prepared" forever and WorkManager runs its job again and again | The EPUB adapter turns running out of memory into "too large", of stack into "too complex" and other runtime exceptions into "damaged"; `BookImportProcessor` turns anything else a parser or the storage throws into a failure on the book; an import started ten times without ending is given up as too complex (SR-2, SR-4) |
| DoS | EPUB parser (B1) | Medium | A book built to be slow to read (huge or deeply nested tables of contents, pathological markup) keeps the import busy for hours | A two-minute time limit per parse, checked before every spine document, at every element walked and in the loops that assemble chapters, ending in a typed "too complex" failure; only the first eight levels of a nested table of contents are examined; a cancelled import interrupts the parse (SR-2) |
| DoS | Project Gutenberg markers (B1) | Medium | A paragraph of millions of asterisks makes the regular expressions that find Gutenberg's `*** START OF …` and `*** END OF …` lines retry every position of the run, in quadratic time, so the import never ends | The opening asterisks are matched only from the first asterisk of a run and never given back (`(?<!\*)\*{2,}+\s*+`), so each run is scanned once; 8 million asterisks take well under a second (`GutenbergBoilerplateTest`) |
| Info disclosure / DoS | XML parsing (B1) | Medium | External entities or DTD expansion (XXE, billion laughs) | DTD and external entities disabled on every XML parser |
| DoS | Book metadata (B1) | High | An EPUB, PDF or shared file name carries a title, author list, language or description of several megabytes; stored whole, the book's database row no longer fits in a cursor window, and reading the library crashes the app at every launch | Titles of books, chapters and catalogue entries, authors, languages and descriptions are cut where they are read (SR-13); the library reads only the columns it shows, cut again in SQL; the library and Explore screens turn a read failure into a message instead of a crash |
| DoS | PDF parsing (B1) | Medium | Malformed or gigantic PDF hangs or crashes the app | Parse in a worker with page, time, glyph and buffer limits (SR-4); password-protected files refused; out-of-memory reported as a typed failure so WorkManager does not retry a crash forever |
| DoS | Cover rendering (B1) | Low | A crafted first page crashes or stalls the platform PDF renderer (native code); a crash kills the process, and WorkManager runs the import again, which crashes again | Rendered only after PdfBox read the file, one page downscaled to at most 600 x 900 px, inside the import worker; a failure means no cover; a native call marker (`native.inflight`) names the call in flight, so a cover whose drawing killed the process twice in a row is left out, and an import started ten times without ending is given up (SR-4) |
| DoS | OCR page rendering and recognition (B1) | Medium | A scan with huge or crafted pages exhausts memory, or stalls or crashes the native renderer or the recognizer, the crash repeating at every run of the import | Pages are drawn only after PdfBox read the file within its limits, one at a time, into a bitmap capped at 4,096 px a side and 12 million pixels; ML Kit gets one minute per page; allocation failures end in a typed failure; a page in which the process died twice in a row is kept without text, a file whose opening did so fails as "too complex", and an import started ten times without ending is given up (SR-4, ADR 0015) |
| Tampering | Intents (B2) | Medium | Another app sends a crafted `file://` URI or huge stream | Accept `content://` only, copy with size limit, re-detect type |
| DoS | Intents (B2) | Low | The sharing app's provider throws an exception the app does not expect (security, argument, state) when its file is opened or its name is read, or the same file is shared twice at once and both copies race to the unique index of file hashes, so the app closes | `BookIntake` turns anything a provider or the storage throws into a rejection; books are registered one at a time (a mutex around the duplicate check and the new row), and a row refused by the unique index makes the copy a duplicate, its file deleted (SR-7) |
| Spoofing / Tampering | Downloads (B3) | Medium | Network attacker swaps a voice model or book | HTTPS only, system CAs for the allowlisted hosts only; voice packages refused unless their size and SHA-256 match the values pinned in `NeuralVoices` (SR-6); books validated like any import (signature, size, EPUB limits) |
| Tampering / EoP / DoS | Voice package archive (B3) | Medium | A package archive (bzip2 tar) with `../` or absolute entries, links, devices or named pipes, a huge or endless body, or thousands of entries writes outside the app's folders or fills the storage | Unpacked only after its SHA-256 matched; entry names are only compared with the catalogue's and never used as paths, the kept files are written under fixed names, and nothing is created from any other entry (links, devices, named pipes and other files are skipped); traversal and absolute names refuse the archive, and so does a kept file that is a symbolic or hard link, a device or a named pipe, has another size, appears twice or is missing; at most 2,000 entries and 512 MiB unpacked; unpacked into a staging folder, each kept file flushed to the disk (`fsync`) before the folder is moved into place at the end (SR-1, SR-2; `VoicePackageArchiveTest`) |
| DoS | Voice package download (B3) | Low | A 67–132 MB download fills the phone or never ends | Free space checked first (archive, unpacked files and 50 MB to spare); the body may not exceed the pinned size; 15 s connect and 30 s read timeouts; a lost connection resumes where it stopped (HTTP range); given up after 6 attempts in a row that receive nothing new; Wi-Fi unless the listener accepts mobile data; cancelling waits until WorkManager has cancelled the job, then deletes what arrived; a download that failed can be removed the same way from its card; when the app opens, partial archives and unpacking folders that no waiting or running download will finish are deleted |
| Spoofing / Tampering | Redirects (B3) | Medium | A redirect (or a feed's next-page link) sends the app to another host or to cleartext | Each hop checked against the allowlist and HTTPS before any connection, at most 5 redirects; TLS to other hosts fails anyway (network security config) |
| Tampering / DoS | Catalogue feed (B3) | Medium | A hostile or broken feed carries XXE, a billion laughs, a huge body or thousands of entries, or a title too large for a download job's 10 KB of data | Same safe XML parser as EPUBs (SR-3), 1 MiB per page, at most 100 books per page, only ebook numbers kept from links; titles and authors cut to 300 characters by the feed parser and again before they enter a job's data (SR-13) |
| DoS | Book download (B3) | Low | A huge or endless answer fills storage or a stalled one hangs, or a site that is down keeps a download "waiting" for minutes | 100 MB limit announced or counted, 15 s connect and 30 s read timeouts, temporary file always deleted; no new try starts two minutes after the first, then the download fails and says why; the listener sees what it does and can cancel it ([ADR 0020](decisions/0020-bounded-catalogue-downloads.md)) |
| Info disclosure | Searches and downloads (B3) | Low | Search words and book choices leak to logs or to third parties | Sent only to Gutenberg (its site, or its official mirror for a download the site cannot serve) over HTTPS after the listener acts; never logged (SR-8); no cookies, identifiers or device data; search pages kept only in memory |
| Info disclosure | TTS engine (B4) | Low/Medium | Network-backed voices send book text to a vendor | Default to offline voices; label network voices in the UI |
| Spoofing | Settings intents (B2) | Low | An app that declares the text-to-speech settings action answers the Voices screen's "install voices" button and imitates the system settings | The intent, and its fallback to the system settings, are addressed to the system Settings app (`com.android.settings`) only; when neither opens, nothing happens (`SystemSettingsTest`) |
| Info disclosure / Tampering | Media session (B2) | Low | Another app connects to the exported playback service to see what is being listened to, or to control playback | Untrusted controllers are rejected; media ids must be book UUIDs (optionally with a chapter); the notification's content intent is explicit and immutable; no media button receiver, so playback never starts from the background |
| Tampering | Media session start intents (B2) | Medium | Another app sends the exported service a media button (`ACTION_MEDIA_BUTTON`) or a custom notification action as a start intent: Media3 finds the session by the URI of Media3's default session id, or asks for a session on behalf of an unknown caller, and the media notification's controller, which runs as the app, relays the action, so the app's reader commands (read from a position, sleep timer) and media keys could be triggered from outside | The session has a random id, so its URI is known only to the notification's own pending intents; the service gives its session to trusted controllers only, so the unknown caller of a start intent gets none; the media notification's controller does not get the reader commands (its −15 s and +15 s buttons are player commands); `ReaderSessionCallback` refuses reader commands from any other controller (SR-7) |
| Info disclosure | ML Kit (B5) | Medium | The OCR library logs its usage to Google, or downloads models, and could send data off the phone | Bundled model only, no download path; the `datatransport` upload backend is removed from the merged manifest, so its logs are dropped (ADR 0015, verified by `MergedManifestTest`); the app has `INTERNET` for the catalogue only, and TLS to Google's hosts fails (network security config, ADR 0017) |
| Info disclosure | Libraries with the network (B5) | Medium | A library uses the `INTERNET` permission the catalogue needs to reach its own servers | The network security config trusts no CA for hosts other than the catalogue's (`www.gutenberg.org`, `gutenberg.pglaf.org`) and the voice packages' GitHub hosts, so their TLS connections fail; cleartext is disabled for all |
| Info disclosure | Logs | Low | Book text, file names or the numbers of the books downloaded in logs (WorkManager logs every job's tags and outcome) | No content/file names in release logs; WorkManager logs errors only in release builds, download jobs carry no tag naming their book, and R8 removes verbose, debug and information logging from the release build (SR-8) |
| Info disclosure | Backups | Low | Cloud backup copies reading history | App out of Android backup (`allowBackup="false"`, ADR 0021); device-transfer rules kept |
| Tampering / DoS | Device transfer of voice packages | Low | Moving to a new phone copies about half a gigabyte of voice packages and partial downloads, files that were checked against their pinned SHA-256 only when they were downloaded and that the new phone would trust by their size alone | `files/voices` is left out of device transfer (`data_extraction_rules.xml`): the new phone downloads the voices again, checked against their pins (SR-6, SR-9) |
| Tampering | Supply chain (B5) | Medium | Compromised dependency, wrapper or action | Wrapper validation, pinned Actions, Dependabot, CodeQL, minimal permissions |
| Info disclosure | Repository (B6) | High | A key or keystore is committed | Pre-commit hook, CI scan, GitHub push protection, `.gitignore`, review |
| EoP | Update channel | Medium | A malicious APK replaces the app | Release signing key never leaves the maintainer's control; installs only from the maintainer |
| Info disclosure / Tampering | Phone left in developer mode | Medium | After an install, USB or wireless debugging stays on and the computer stays authorized, so anyone with the phone (or on the same network) can install apps, read logs or, with a debuggable build, the app's private data | Only release builds are installed; after every install the runbook turns wireless and USB debugging off, revokes USB debugging authorizations and turns developer options off ([install runbook](runbooks/install-on-device.md), step 4) |

## Security requirements

Each requirement is testable; reference its ID in tests and reviews.

- **SR-1** Archive entry names are never used to build file-system paths
  (verified by `EpubSecurityTest`; for voice packages, whose kept files are
  written under the catalogue's names and whose traversal or absolute entries
  refuse the archive, by `VoicePackageArchiveTest`).
- **SR-2** EPUB reading enforces limits: entry count, per-entry and total
  uncompressed size, the text extracted, and time; violations yield a typed
  `ImportError` (verified by `EpubSecurityTest`). (Defaults: 10,000 entries,
  16 MiB per entry and 256 MiB in all, inflated bytes counted as they are
  read; at most 20 million characters of text from the spine, counted as
  paragraphs are cut, else `LimitExceeded(MAX_TEXT_CHARS)`, shown as "too
  large". `ArchiveLimits.maxParseTime`, two
  minutes, is checked before every spine document, at every element of a
  content document or table of contents and in the loops that assemble
  chapters, and a parse past it fails with `EpubError.TooComplex`, shown as
  "too complex". Only the first eight levels of a nested table of contents
  are examined (`TocDepth`), and Project Gutenberg's markers are matched in
  linear time. Interrupting the parsing thread, as a cancelled import does,
  stops the parse with `InterruptedException`, also when jsoup had already
  read the rest of a document as missing. Verified by `EpubSecurityTest`,
  `EpubChapterTest` and `GutenbergBoilerplateTest`. Whatever else a hostile
  file provokes ends in a failure on the book, never in an import left
  running: `EpubBookParser` maps running out of memory to "too large", of
  stack to "too complex" and other runtime exceptions to "damaged";
  `BookImportProcessor` turns anything else a parser or the storage throws
  into a failure; `ImportBookWorker` gives up an import started ten times
  without ending as "too complex", which also ends a loop of crashes in
  native code. Verified by `EpubBookParserTest`, `ImportPipelineTest` and
  `ImportBookWorkerTest`.) Voice package archives are unpacked within limits
  too: at most 2,000 entries and 512 MiB unpacked, kept files regular (no
  links, devices or named pipes) and of exactly their pinned sizes, once each
  (`VoicePackageArchive`, verified by `VoicePackageArchiveTest`).
- **SR-3** Every XML parser disables DTDs and external entities (verified by
  `EpubSecurityTest`, and for catalogue feeds by `GutenbergFeedParserTest`).
- **SR-4** PDF extraction and OCR run with page, time and memory limits.
  (`PdfLayoutExtractor` with `PdfLimits`: documents over 3,000 pages are
  refused, before any text is read when they declare it and while reading
  when the declared count lies; extraction stops after 5 minutes, checked
  before every page and every 1,024 glyphs or content stream operators, and
  as soon as its import is cancelled; at most 20 million glyphs are met per
  document and 100,000 kept per page; PdfBox buffers at most 16 MiB in memory and 512 MiB in all, the rest
  in a temporary file in the app cache; the outline reader visits at most
  5,000 entries, 32 levels deep, and stops at loops; running out of memory or
  stack ends in a typed failure. Verified by `PdfSecurityTest`. Text
  recognition (`PdfOcr` with `OcrLimits`, ADR 0015) refuses books with more
  than 1,500 pages to recognize before drawing any; stops after 60 minutes of
  recognition per book, counting the pages stored by earlier runs; gives ML
  Kit one minute per page; draws one page at a time in the whole app into a
  bitmap of at most 4,096 pixels a side and 12 million pixels; and ends in a
  typed failure when a bitmap cannot be allocated or the recognizer fails.
  Stored recognized pages over 4 MiB, of another format version or that do
  not parse are ignored and recognized again. Verified by `PdfOcrTest`,
  `OcrLimitsTest` and `OcrPageStoreTest`. A crash in native code (the
  platform renderer, ML Kit) kills the process with nothing to catch, so
  before each native call, drawing the cover, opening the file for
  recognition or recognizing a page, the import notes the call in
  `books/{id}/native.inflight` (`NativeCallMarkers`) and deletes the note when
  the call returns; the next run reads a note left behind and, once the
  process died in the same call twice in a row, leaves the cover out, keeps
  the page without text, or fails as "too complex" when opening the file
  crashed. Verified by `NativeCallMarkersTest`, `PdfOcrTest` and
  `PdfBookParserTest`.)
- **SR-5** Cleartext traffic is disabled (`network_security_config`); only
  allowlisted HTTPS hosts are contacted, and only after a user action. (ADR
  0017: `HttpsClient` (as `CatalogHttp`, for the catalogue) is the app's only network client and sends a request
  only when the listener searches, asks for more results or downloads a book;
  a queued download waits for a connection, tries again after a lost
  connection or a site that is down or busy, but starts no new try two minutes
  after the first, and can be cancelled (ADR 0020). Every address and every redirect
  must be HTTPS on the default port, without user information, on exactly
  `www.gutenberg.org` or Gutenberg's official mirror `gutenberg.pglaf.org`
  (`CatalogHosts`, ADR 0019); at most 5 redirects; only a 200 answer of the
  expected media type is read (an EPUB may come as `application/epub+zip` or
  `application/octet-stream`, and the intake checks its signature), within
  1 MiB for a feed page and 100 MB for a book. A download asks the mirror when
  the site answers 5xx or 429 or cannot be reached. The network security
  config disables cleartext and trusts the system CAs for these two names
  only, no subdomains, so TLS to any other host fails. ADR 0018: voice
  packages are downloaded only when the listener taps "Download" on a voice,
  by `VoiceModelHttp` from `github.com` (the official sherpa-onnx release),
  following its redirect to `release-assets.githubusercontent.com` (or
  `objects.githubusercontent.com`, its former asset host): exact names, HTTPS
  on the default port, also trusted by the network security config with the
  system CAs only. `INTERNET` and `ACCESS_NETWORK_STATE` are declared; ML Kit's
  `datatransport` upload backend stays removed. Verified by `CatalogHostsTest`,
  `CatalogHttpTest`, `HttpsClientTest`, `NetworkSecurityConfigTest`,
  `VoicePackageDownloaderTest` and `MergedManifestTest`.)
- **SR-6** Downloaded voice models are verified against SHA-256 values pinned
  in code before use. (`NeuralVoices` pins each package archive's size and
  SHA-256, the digests GitHub publishes for the release assets, checked against
  downloads; `VoicePackageDownloader` refuses a body larger than the pinned
  size, checks size and SHA-256 of the whole archive, also after a resumed
  download, deletes an archive that does not match and only then unpacks it.
  A partial archive is named after its package and pinned SHA-256
  (`{id}-{first 16 hex digits}.part`), so it is only ever resumed into the
  archive it was started for. Verified by `VoicePackageDownloaderTest`.
  Catalogue books are not pinned: Gutenberg rebuilds its EPUBs every month.
  They rely on HTTPS to an allowlisted host and are validated like any
  imported file, SR-1 to SR-3 and SR-7.)
- **SR-7** Exported components are limited to the launcher/intake activity and
  the media session service; intake accepts only `content://` URIs. (`BookIntake`; content is re-detected by signature and copied with a
  300 MB limit. Whatever the sharing app's provider throws when its file is
  opened, read or named, and any storage error, is a rejection, never a
  crash; books are registered one at a time, and a file another intake added
  first is reported as a duplicate with its copy deleted. Verified by
  `BookIntakeTest` and `ImportViewModelTest`. `PlaybackService` must be exported for the system to bind
  it; `ReaderSessionCallback` accepts only trusted controllers — this app,
  the system and apps the user allowed to control media — and only book ids,
  and the service hands its session (`onGetSession`) to trusted controllers
  only. Media buttons and custom notification actions that other apps send to
  the service as start intents are ignored: the session has a random id, so the
  session URI Media3 looks such intents up by is known only to the
  notification's own pending intents, and the unknown caller Media3 asks about
  otherwise is not trusted. The app's custom session commands
  (`ReaderCommands`) are granted only to controllers running as this app's uid
  other than Media3's media notification controller, which relays notification
  actions; they are refused from any other controller, and their arguments are
  validated before they reach the player. Verified by
  `ReaderSessionCallbackTest`, `MergedManifestTest` (the exact set of exported
  components) and, on a device, `PlaybackServiceTest`.
  Media3 also merges its `BluetoothValidationActivity`, exported but guarded
  by the `BLUETOOTH_PRIVILEGED` permission that only the system Bluetooth app
  holds.)
- **SR-8** Release builds log no book content, file names or personal data. (`Diagnostics` logs only
  exception types. Catalogue addresses, which hold the search words, and the
  downloaded books' titles are never logged. WorkManager logs every job's
  tags and outcome at the information level, so it logs only errors in
  release builds (`RReaderApplication`), and catalogue download jobs carry no
  tag naming their book: they are found by their unique names. R8 removes
  verbose, debug and information logging calls from the release build,
  libraries included (`proguard-rules.pro`); warnings and errors stay.
  Verified by `CatalogDownloadsTest` and `RReaderApplicationTest`.)
- **SR-9** The app is out of Android backup (`android:allowBackup="false"`), so nothing goes
  to the cloud and no backup stops playback; device-to-device transfer follows
  `data_extraction_rules` where the phone applies it (ADR 0021): books,
  positions and settings, but not the voice packages (`files/voices`, with
  partial downloads), which a new phone downloads again and checks against
  their pinned SHA-256. Checked by `MergedManifestTest`.
- **SR-10** No secrets in the repository (see the layers below).
- **SR-11** Dependencies are pinned in the version catalog and monitored by
  Dependabot; CI runs CodeQL and validates the Gradle wrapper. Libraries that
  exist only as release files (sherpa-onnx, piper-phonemize) are downloaded
  by the build from their GitHub releases into `app/libs/` (not kept in git),
  and `fetchVendoredLibraries` refuses any file that does not match its
  SHA-256 in `app/libs/SHA256SUMS` ([app/libs/README.md](../app/libs/README.md)). (Known
  vulnerabilities that cannot be reached in a dependency are listed, with the
  reason, under [Known vulnerabilities in dependencies](#known-vulnerabilities-in-dependencies).)
- **SR-12** No `WebView`, dynamic code loading or reflection-based plugins.
- **SR-13** Descriptive text read from untrusted files and feeds is bounded
  before it is stored or shown. (textkit's `MetadataLimits`, applied by the
  EPUB package reader, `DocumentInfoCleaner`, every chapter built through
  `Chapter.fromParagraphs` and, for a title made from a file name, by
  `BookIntake`: titles of books, chapters and catalogue entries are cut to 300
  characters, authors to 20 names of at most 300 characters, descriptions to
  4,000 characters, and a language is kept only as a BCP 47-like tag of at
  most 35 characters. Values over a limit are cut, never rejected. The library
  query reads only the columns the library shows, the title and authors cut
  again in SQL, so even a row stored before these limits fits in a 2 MB cursor
  window; the library and Explore screens catch a failure to read the library
  and show a message with "Try again" instead of crashing. Verified by
  `MetadataLimitsTest`, `EpubSecurityTest`, `DocumentInfoCleanerTest`,
  `ChapterTest`, `BookDaoTest`, `ImportPipelineTest`, `LibraryViewModelTest`
  and `ExploreViewModelTest`.)

## Permissions

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Listening with the screen locked runs in a `mediaPlayback` foreground service ([ADR 0014](decisions/0014-target-android-17.md)). |
| `FOREGROUND_SERVICE_DATA_SYNC` | Text recognition of a scanned book runs in WorkManager's foreground service with the `dataSync` type, the type for importing and processing local files ([ADR 0015](decisions/0015-on-device-ocr-with-bundled-ml-kit.md)). |
| `POST_NOTIFICATIONS` | Lets that service show its progress on Android 13+ once the user allows notifications in the system settings. Never requested: the library shows the progress and the work runs the same without the notification (ADR 0015). |
| `WAKE_LOCK` | The player keeps the CPU awake while reading with the screen off. |
| `INTERNET` | Catalogue searches and book downloads from Project Gutenberg, and voice package downloads from GitHub, after a user action, over HTTPS to allowlisted hosts only ([ADR 0017](decisions/0017-free-catalogue-from-project-gutenberg.md), [ADR 0018](decisions/0018-offline-neural-voices.md)). |
| `ACCESS_NETWORK_STATE` | The Explore tab says when the phone is offline, and WorkManager waits for a connection before a download (ADR 0017); ExoPlayer and ML Kit's `datatransport` declare it too. |
| `RECEIVE_BOOT_COMPLETED` | Merged from WorkManager, which reschedules pending import jobs after a restart. |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature permission defined by AndroidX Core to keep runtime receivers private. |

No runtime permission is requested: the playback notification is a media
notification, exempt from the notification permission, and the import
progress notification is optional. Other exported
components merged from libraries require system-only permissions
(`BIND_JOB_SERVICE`, `DUMP`), and the tooling activities exist only in debug
builds. ML Kit adds only components that are not exported: its
initialization provider, component discovery services, the
`datatransport` scheduler (job service and alarm receiver) and Google Play
services' `GoogleApiActivity`. The app declares `INTERNET` for the
catalogue (ADR 0017); the `datatransport` upload backend that ML Kit brings
stays removed, and the network security config lets TLS reach Gutenberg only.

## Known vulnerabilities in dependencies

- **Bouncy Castle 1.72** (`bcprov`, `bcpkix` and `bcutil`, `-jdk15to18`),
  which PdfBox-Android 2.0.27.0 brings. Scanners report vulnerabilities fixed
  in later releases (1.73 to 1.78): an LDAP injection in its LDAP certificate
  store, denial of service in PEM and ASN.1 parsing, excessive CPU with
  crafted binary-field elliptic curves, an endless loop when checking
  Ed25519 signatures, and flaws in its TLS code. None can be reached in
  R Reader: the app reaches Bouncy Castle only through PdfBox's decryption of
  PDFs that open without a password (RC4 and AES); it never parses
  certificates, CMS, PEM or keys, never checks signatures, never uses LDAP,
  and has no TLS through Bouncy Castle (its TLS library is not included; the
  catalogue uses the platform's TLS). PDFs encrypted for certificate holders
  are refused before their recipients are read, since PdfBox needs a key
  store the app never gives it. Bouncy Castle is not upgraded on its own, so
  that it stays the version PdfBox-Android is built and tested with; review
  it again whenever PdfBox-Android is updated.

## Secret protection layers

1. `.gitignore` covers secrets, keystores, env files and release binaries.
2. `secrets.properties` (git-ignored) is the only place for keys, read by
   Gradle; `secrets.defaults.properties` holds placeholders. Keys compiled
   into an APK are extractable, so a truly sensitive key would require a
   server, which is out of scope ([ADR 0013](decisions/0013-layered-secret-protection.md)).
3. `.githooks/pre-commit` blocks secret-bearing files and runs gitleaks on the
   staged diff. Never bypass it.
4. CI scans the full history with gitleaks on every push.
5. GitHub secret scanning and push protection are enabled.
6. The release keystore and its passwords live outside the repository.

Leak response: [runbooks/secrets.md](runbooks/secrets.md).

## Privacy

- Data stays on the device. There is no account, server, analytics or ads.
- Network calls: catalogue searches, book downloads and voice package
  downloads, each started by the user. Project Gutenberg sees the phone's
  IP address, the search words, the numbers of the books downloaded and the
  app's User-Agent (name, version and, when the build sets one, the
  maintainer's contact); its official mirror sees the same for a download the
  site could not serve; no cookies, identifiers or device data are sent.
  Showing the Explore tab, including its curated list, makes no request.
  GitHub sees the phone's IP address, which voice packages are downloaded and
  the app's name and version (no contact address); showing the Voices tab and
  playing the bundled samples makes no request. The neural voices then run
  entirely on the phone: book text never leaves it.
- The system TTS engine is a separate app chosen by the user; offline voices
  are preferred so book text does not leave the phone.

## Review checklist (end of each phase)

- [ ] New inputs or network calls are reflected in this document.
- [ ] SR-1 … SR-13 still hold; new tests cover new parsing paths.
- [ ] Exported components and permissions unchanged or justified.
- [ ] No secrets, personal data or third-party branding in the diff.
- [ ] Dependency changes reviewed (Dependabot PRs or manual bumps).
