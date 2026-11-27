# 0015. On-device OCR with bundled ML Kit, resumable per page, in a dataSync foreground job

- **Status:** Accepted; the removal of `INTERNET` in decision 1 is superseded by
  [ADR 0017](0017-free-catalogue-from-project-gutenberg.md), which keeps the
  upload backend removed
- **Date:** 2026-11-27

## Context

[ADR 0005](0005-pdf-extraction-pdfbox-and-mlkit-ocr.md) chose ML Kit Text
Recognition v2 for PDF pages without a text layer. Making that work on the
primary user's phone (Android 17) raises questions ADR 0005 left open:

- ML Kit comes in two flavours: a **bundled** model inside the APK
  (`com.google.mlkit:text-recognition`, about 7–12 MB of native code per CPU
  architecture) and an **unbundled** one that Google Play services downloads
  on first use.
- ML Kit and its Firebase `datatransport` dependency log API usage to Google
  and declare the `INTERNET` permission, while the app must not send anything
  without a user action ([ADR 0012](0012-no-accounts-local-only-data.md)).
- Recognizing a 300-page scan takes minutes (render each page, then
  recognize it). Android may kill the process at any time, and WorkManager
  jobs that are not in a foreground service are stopped after about
  10 minutes.
- Since Android 14 a foreground service must declare a type that matches its
  work, and apps that target Android 14+ need the matching permission.
  Since Android 13 notifications need a runtime permission, but a foreground
  service still runs when it is denied (the notification then only shows in
  the system's task manager). Android 12+ refuses to start foreground
  services from the background.
- The library has to show how far the recognition got.

## Decision

1. **Bundled model, offline, no telemetry.** Use the bundled Latin model
   (`com.google.mlkit:text-recognition`, `TextRecognizerOptions.DEFAULT_OPTIONS`);
   there is no download path. The merged manifest removes `INTERNET` and the
   `datatransport` upload backend (`CctBackendFactory`), so ML Kit's usage
   logs are dropped on the phone (checked by `MergedManifestTest`).
2. **Which pages.** A page is recognized when it has no usable text layer
   (`ScannedDocumentDetector.pagesWithoutText`: fewer than 20 letters and
   digits outside running lines) and it draws something text could be
   pictured in: an image, at least 1,000 path segments (text drawn as
   outlines), or content PdfBox could not read. Blank pages and short pages of
   text PDFs are therefore left alone. All pages, recognized or not, then go
   through the same `LayoutBookBuilder`, so mixed PDFs keep their page order,
   running lines, hyphenation and chapters. A document whose pages still have
   no readable text after recognition fails with `SCANNED_PDF`.
3. **Rendering and recognition.** The platform `PdfRenderer` draws one page at
   a time on white at 300 dpi, capped at 4,096 pixels on the longest side and
   12 million pixels (about 46 MiB as ARGB_8888), and the bitmap is recycled
   after recognition. A process-wide gate lets only one page be rendered and
   recognized at a time, even when several imports run. ML Kit's line boxes
   are scaled back to PDF points (top-left origin), the coordinates of PdfBox
   lines, and the font size is estimated from the box height: the height of
   a line with lower-case letters, the height divided by 0.7 for a line of
   capitals or digits, and at least the median of the lines of its block.
4. **Resumable work.** Each recognized page is written at once to
   `files/books/{bookId}/ocr/{page}.json` (versioned JSON through
   kotlinx.serialization, written to a temporary file and renamed), with the
   time its recognition took. A new run of the import skips stored pages; a
   page file that cannot be read back is recognized again. The page files
   are deleted when the import ends (book ready or failed), so a retry after
   a failure starts over. A removed book's import is cancelled and its files
   are deleted, also files the cancelled job writes afterwards.
5. **Limits (SR-4).** At most 1,500 pages to recognize per book; 60 minutes
   of recognition per book, counting the stored pages of earlier runs; one
   minute for ML Kit per page; a page whose bitmap cannot be allocated ends
   the import. Each limit ends in a typed failure with a clear message: too
   many pages to recognize has its own, time and memory share the "too
   complex" one, and a failing recognizer has its own.
6. **Foreground job of type `dataSync`.** The import stays a unique
   WorkManager job per book; when recognition starts, the worker calls
   `setForeground` with a silent, low-importance progress notification and
   the `dataSync` type (`FOREGROUND_SERVICE_DATA_SYNC`, and WorkManager's
   `SystemForegroundService` declared with that type). If the platform
   refuses the foreground service, for example because the job restarted
   while the app is in the background, the work goes on as a normal job:
   the system may stop it after about 10 minutes and WorkManager runs it
   again later, resuming from the stored pages. The app declares
   `POST_NOTIFICATIONS` but does not ask for it.
7. **Progress through WorkManager.** The worker publishes the page being
   recognized and the number of pages with `setProgress`; `ImportJobs`
   observes the running import jobs and the library shows "Recognizing text:
   page X of Y". Nothing is added to the database.

## Alternatives considered

- **Unbundled ML Kit (Google Play services)** — a smaller APK, but the model
  is downloaded over the network on first use and needs Google Play services
  on the phone.
- **Tesseract** (open source, through an Android port) — no proprietary code,
  but slower on phones, a native build to maintain and a separate language
  model to ship; ADR 0005 already preferred ML Kit.
- **Cloud OCR** — conflicts with ADR 0012.
- **Other foreground service types** — `mediaProcessing` (Android 15+) is
  meant for transcoding audio and video; `specialUse` is for work no other
  type describes and needs a written justification; `shortService` stops
  after about three minutes. `dataSync` is the type Android documents for
  importing and processing local files. User-initiated data transfer jobs
  are for network transfers and are not available through WorkManager.
- **No foreground service** — the 10-minute job limit and process deaths
  would make a long recognition crawl through restarts.
- **Progress in a Room column** — survives process death but needs a
  migration and a write per page; WorkManager already keeps the progress of
  running work.
- **Asking for the notification permission** — a system dialog while adding
  a book would confuse the primary user, and the library already shows the
  progress.
- **Recognizing every page without a usable text layer** — simpler, but text
  PDFs with blank or short pages would be recognized needlessly, and their
  text layer replaced.

## Consequences

- Positive: scanned and mixed PDFs import fully offline; a long recognition
  survives process death and system stops without redoing finished pages;
  the app still has no network access.
- Negative / costs: the APK grows by the native library for each CPU
  architecture; ML Kit is proprietary (terms in
  [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)); recognition quality
  depends on the scan, and two-column pages are read in the recognizer's
  order. Android 15+ limits `dataSync` services to 6 hours a day, far above
  the 60-minute budget, and Android says the type may be deprecated later.
- Follow-ups: check the font-size estimate and the time per page on the
  primary user's phone; consider building the phone's APK for `arm64-v8a`
  only; when a later phase adds `INTERNET` for downloads, keep the upload
  backend removed and adapt `MergedManifestTest`.
