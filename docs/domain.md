# Domain glossary

Names used in code and docs. Add a term here when you introduce it.

| Term | Meaning |
|------|---------|
| **Book** | An imported title: metadata (title, author, language, format), its chapters and its reading state. Identified by `BookId`. |
| **Source file** | The original PDF/EPUB copied into private app storage at import time. |
| **Book format** | `EPUB` or `PDF`. Detected from the file signature, never from the file name alone. |
| **Import** | Turning a source file into a normalized book. Runs as a background job and ends `READY` or `FAILED` with a user-facing reason. |
| **Import progress** | How far a running import got, when a long stage reports it (`ImportProgress`): today, text recognition reading page X of Y. Published through WorkManager and shown on the book's row and in the import notification; not stored. |
| **Intake** | The synchronous first half of an import (`BookIntake`): verify the signature, copy with a size limit while hashing, reject duplicates, create the book row and queue the import job. |
| **Normalized book** | The cleaned, speakable text of a book split into chapters and paragraphs. Immutable after import. |
| **Chapter** | An ordered section of a book with a title and normalized text. From the EPUB table of contents (whole files, or the anchors its entries point to inside them, with chapter headings splitting sections longer than about an hour), the PDF outline, detected headings, or page ranges as a fallback. In EPUB books, very short sections (title-page lines, part titles, "FIM") join a neighbouring chapter of the same file. |
| **Anchor** | An element of an EPUB content file named by its `id` that a table-of-contents entry points to (`chapter.xhtml#chap02`). A chapter starts at the paragraph the anchor opens, so one file can hold many chapters. |
| **Paragraph** | A block of chapter text separated by a blank line in normalized form. |
| **Sentence** | A unit produced by the sentence segmenter: pt-BR aware (abbreviations, initials, decimals, dialogue dashes) and never across a line break or paragraph. Sentences longer than 280 characters are split at punctuation. |
| **Segment** | One or more consecutive sentences rendered as a single audio clip. The unit of rendering, playback items and highlighting. |
| **Segment planner** | Groups a chapter's sentences into segments: a sentence shorter than 24 characters is merged with the next one in the same paragraph, up to 320 characters. Also finds the segment holding a reading position, whose start is where playback resumes. |
| **Layout line** | A line of text with its page, bounding box and font size, produced by PDF extraction or OCR and consumed by the cleanup step. |
| **Cleanup** | Removing repeated headers/footers and page numbers, joining hyphenated words (keeping Portuguese enclisis such as "fazê-lo"), and rebuilding paragraphs from layout lines. |
| **Speech normalization** | Small, conservative rewrites applied only when audio is rendered (never to the stored chapter text): Roman numerals to numbers in headings and after section words (`século XIX` → `século 19`), ALL-CAPS heading words to sentence case, symbols that voices read badly removed, repeated `!`/`?` collapsed, `...` to `…`. |
| **Reading position** | `(chapterIndex, charOffset)` into normalized chapter text. The single source of truth for "where am I". |
| **Progress** | Fraction of the book's characters before the reading position. Shown as a percentage. |
| **Speaking rate** | Characters per second for a voice at 1.0×, learned from rendered segments with an exponential moving average (14.5 until measured) and used for time estimates. Kept per voice across restarts, with the voice that read last. |
| **Time left** | Estimate: remaining characters ÷ speaking rate ÷ playback speed, rounded to the minute for display ("Faltam 3 h 20 min", "Falta menos de 1 min"). The library uses the rate of the book's voice, else the default voice, else the voice that read last; the full player shows it for the chapter and the book from the facade's timeline. |
| **Playback speed** | User-chosen multiplier (0.75–2.0×), applied by the player with pitch preserved. |
| **Voice** | A selectable speaker, identified by engine + voice id + locale. Voice ids are `system:<engine>:<name>` for a phone voice, `neural:<key>` for a neural voice, or `phone` for the phone's own voice chosen automatically (`VoiceIds`). |
| **Neural voice** | An offline neural voice run in the app by sherpa-onnx (ADR 0018): Dora, Alex and Santa (Kokoro), Cadu, Faber, Jeff, Dii, Miro and Edresson (Piper), listed most recommended first in `NeuralVoices`. Usable once its voice package is installed. |
| **Voice package** | A downloadable archive of the official sherpa-onnx releases holding one voice model (`VoicePackage`): the Kokoro package serves three voices, each Piper voice has its own. Pinned by size and SHA-256, installed in `filesDir/voices/{package}`, deleted from the Voices tab. |
| **Speech engine** | Implementation of `SpeechEngine` that renders a segment to audio: `SystemTtsEngine` (Android TTS) or `SherpaOnnxEngine` (on-device neural models). |
| **Segment renderer** | `SegmentRenderer`: renders one segment's speech to a WAV file in the audio cache (reusing a cached file when it is valid), feeds the speaking rate and trims the cache. |
| **Render-ahead window** | The rendered audio queued after the playing segment: at least two minutes and four segments, continuing into the next chapters. Kept full by the `SegmentFeeder`. |
| **Spoken segment** | The segment the voice is reading now, or will read right after a seek: book, chapter and character range. The player publishes it in `ListeningStatus` for the follow-along reader. |
| **Listening status** | `ListeningStatus`: the in-process holder through which the player tells the UI what Media3's player state does not model, such as the spoken segment. Read-only for the UI. |
| **Reader command** | A command the app sends to the playback service as a custom session command (`ReaderCommands`), such as "read from here" with a reading position, or "the voice changed". Only the app's own controllers may send them, not the media notification's. |
| **Phone's voice** | The voice choice `phone` (`VoiceIds.PHONE`): the best offline Portuguese voice of the phone's own engines, whichever it is today. The default when nothing was chosen, and where listening falls back when a chosen neural voice is deleted. |
| **Segment span** | A segment's characters plus the whitespace up to the next segment (or the end of the chapter). The position inside a playing segment advances through its span, so it reaches the next segment's start without gaps. |
| **Media time** | The facade player's timeline in a chapter: characters ÷ speaking rate at 1.0×. Positions, durations and ±15 s are expressed in it; playback speed changes how fast it passes, not its length. |
| **Facade player** | `ReaderPlayer`: the Media3 player the session sees. It presents one item per chapter with a continuous position and duration in media time while an internal ExoPlayer plays rendered segments, and maps seeks, ±15 s and headset next/previous onto reading positions. |
| **Playback connection** | `PlaybackConnection`: the UI's `MediaController` link to the playback service, connected while the app is visible; ViewModels use it through `PlaybackControls`. |
| **Mini player** | The bar above the bottom navigation while a book is open: cover, title, chapter, −15 s and play/pause. Tapping it opens the full player. |
| **Full player** | The player screen: cover, book and chapter titles, chapter progress with elapsed and remaining time, the time left in the chapter and the book, −15 s, play/pause, +15 s, speed, the sleep timer and the chapter list. |
| **Follow-along reader** | The screen that shows the chapter being read in large, adjustable text, highlights the segment being spoken and keeps it in view, and reads from a tapped sentence. Opened with "Text" in the full player. |
| **Auto-follow** | The reader keeping the highlighted segment in view as reading moves on; scrolling away stops it until "Back to the reading". |
| **Read from here** | Tapping a sentence in the reader: listening continues from the start of the segment that holds it. |
| **Sleep timer** | Pauses listening after a duration of listening (the player offers 5–60 minutes) or at the end of the chapter being listened to, fading the volume out over the last 10 seconds; the position is saved. Its time runs only while audio plays. Lives in the facade player (`SleepTimer`), so it works with the screen off. |
| **Resume rewind** | After a pause of more than 5 minutes (or a book left that long), listening resumes one segment or about 10 seconds of text earlier, whichever is less, from the start of the segment that holds that point; never before the start of the book. Seeks and chapter jumps cancel it. |
| **Fade-out** | The last 10 seconds of a sleep timer, while the internal player's volume falls to zero before the pause; the full volume is restored after pausing. |
| **Catalogue entry** | A public-domain book offered for download in the Explore tab (`CatalogEntry`): its Project Gutenberg ebook number, title and author, and for curated classics a short description. |
| **Curated classics** | The catalogue entries the Explore tab shows before any search: well-known books in Portuguese chosen for the app and shipped with it (`assets/catalog/curated.json`), so showing them needs no network. Many keep the spelling of their time. |
| **Catalogue search** | Looking for books in Portuguese in Project Gutenberg's catalogue by title or author: one request for a page of 25 results, the next page only when the listener asks for more (`CatalogRepository`). |
| **Catalogue download** | Getting a catalogue entry's EPUB from Project Gutenberg into the library (`CatalogDownloader`, run by `DownloadBookWorker`): a background job that waits for a connection, retries failures that may pass and hands the file to the intake like any shared file. |
| **Download state** | Where a catalogue download stands, by ebook number (`DownloadState`): waiting (for a connection or the next attempt), downloading (with a percentage once the size is known), in the library (just added or already there) or failed with a reason. |
| **Gutenberg boilerplate** | The English text Project Gutenberg adds around its books: the header (title, release date, terms of use) up to the `*** START OF THE PROJECT GUTENBERG EBOOK … ***` line, the production credits right after it, and the license from the `*** END OF … ***` line on. Removed from the chapter text at import (`GutenbergBoilerplate`), so it is never read aloud; the stored source file keeps it. |
| **OCR page** | A PDF page without a usable text layer whose text may be a picture (it draws an image, text turned into outlines, or content PdfBox could not read), so its text is recognized on-device with ML Kit. Blank pages and short pages of text PDFs are not OCR pages. |
| **Recognized page** | An OCR page as text recognition read it, stored in the book's `ocr` folder as soon as it is read so a stopped import resumes after it, and deleted when the import ends. |
| **Page layout** | One page as PDF extraction or OCR sees it: its size and its layout lines in reading order (`PageLayout`). |
| **Recognized line** | A line of text an OCR engine found on the picture of a page, with its box in the picture's pixels (`RecognizedLine`); a **recognized block** groups the lines the engine read together, usually a paragraph. `OcrPageLayout` turns them into layout lines in page units. |
| **Outline entry** | A PDF bookmark: a title, the page it points to and optionally a position on that page (`OutlineEntry`). Marks where a chapter starts. |
| **Margin band** | The top and bottom 10% of a page's height, where running lines are looked for. |
| **Running line** | A running header or footer or a page number printed in a margin band; removed by cleanup. |
| **Enclisis** | An unstressed pronoun joined to a verb with a hyphen ("fazê-lo", "dá-lhe"), or placed inside it (mesoclisis, "amá-lo-ei"). Cleanup keeps these hyphens when it joins words broken across lines. |
| **Scanned PDF** | A PDF whose pages are pictures of text: their pages have fewer than 20 letters and digits outside running lines. Its OCR pages are read by text recognition during the import; it fails as scanned only when more than half of its pages still have no readable text afterwards. |
| **Document info** | The title, author and language a PDF declares about itself; kept only when meaningful (not a file name, placeholder or computer user name). |
