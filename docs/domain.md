# Domain glossary

Names used in code and docs. Add a term here when you introduce it.

| Term | Meaning |
|------|---------|
| **Book** | An imported title: metadata (title, author, language, format), its chapters and its reading state. Identified by `BookId`. |
| **Source file** | The original PDF/EPUB copied into private app storage at import time. |
| **Book format** | `EPUB` or `PDF`. Detected from the file signature, never from the file name alone. |
| **Import** | Turning a source file into a normalized book. Runs as a background job and ends `READY` or `FAILED` with a user-facing reason. |
| **Intake** | The synchronous first half of an import (`BookIntake`): verify the signature, copy with a size limit while hashing, reject duplicates, create the book row and queue the import job. |
| **Normalized book** | The cleaned, speakable text of a book split into chapters and paragraphs. Immutable after import. |
| **Chapter** | An ordered section of a book with a title and normalized text. From the EPUB table of contents, the PDF outline, detected headings, or page ranges as a fallback. |
| **Paragraph** | A block of chapter text separated by a blank line in normalized form. |
| **Sentence** | A unit produced by the sentence segmenter: pt-BR aware (abbreviations, initials, decimals, dialogue dashes) and never across a line break or paragraph. Sentences longer than 280 characters are split at punctuation. |
| **Segment** | One or more consecutive sentences rendered as a single audio clip. The unit of rendering, playback items and highlighting. |
| **Segment planner** | Groups a chapter's sentences into segments: a sentence shorter than 24 characters is merged with the next one in the same paragraph, up to 320 characters. Also finds the segment holding a reading position, whose start is where playback resumes. |
| **Layout line** | A line of text with its page, bounding box and font size, produced by PDF extraction or OCR and consumed by the cleanup step. |
| **Cleanup** | Removing repeated headers/footers and page numbers, joining hyphenated words (keeping Portuguese enclisis such as "fazê-lo"), and rebuilding paragraphs from layout lines. |
| **Speech normalization** | Small, conservative rewrites applied only when audio is rendered (never to the stored chapter text): Roman numerals to numbers in headings and after section words (`século XIX` → `século 19`), ALL-CAPS heading words to sentence case, symbols that voices read badly removed, repeated `!`/`?` collapsed, `...` to `…`. |
| **Reading position** | `(chapterIndex, charOffset)` into normalized chapter text. The single source of truth for "where am I". |
| **Progress** | Fraction of the book's characters before the reading position. Shown as a percentage. |
| **Speaking rate** | Characters per second for a voice at 1.0×, learned from rendered segments with an exponential moving average (14.5 until measured) and used for time estimates. |
| **Time left** | Estimate: remaining characters ÷ speaking rate ÷ playback speed. |
| **Playback speed** | User-chosen multiplier (0.75–2.0×), applied by the player with pitch preserved. |
| **Voice** | A selectable speaker, identified by engine + voice id + locale. |
| **Speech engine** | Implementation of `SpeechEngine` that renders a segment to audio: `SystemTtsEngine` (Android TTS) or `SherpaOnnxEngine` (on-device neural models). |
| **Segment renderer** | `SegmentRenderer`: renders one segment's speech to a WAV file in the audio cache (reusing a cached file when it is valid), feeds the speaking rate and trims the cache. |
| **Render-ahead window** | The rendered audio queued after the playing segment: at least two minutes and four segments, continuing into the next chapters. Kept full by the `SegmentFeeder`. |
| **Segment span** | A segment's characters plus the whitespace up to the next segment (or the end of the chapter). The position inside a playing segment advances through its span, so it reaches the next segment's start without gaps. |
| **Media time** | The facade player's timeline in a chapter: characters ÷ speaking rate at 1.0×. Positions, durations and ±15 s are expressed in it; playback speed changes how fast it passes, not its length. |
| **Facade player** | `ReaderPlayer`: the Media3 player the session sees. It presents one item per chapter with a continuous position and duration in media time while an internal ExoPlayer plays rendered segments, and maps seeks, ±15 s and headset next/previous onto reading positions. |
| **Playback connection** | `PlaybackConnection`: the UI's `MediaController` link to the playback service, connected while the app is visible; ViewModels use it through `PlaybackControls`. |
| **Mini player** | The bar above the bottom navigation while a book is open: cover, title, chapter, −15 s and play/pause. Tapping it opens the full player. |
| **Full player** | The player screen: cover, book and chapter titles, chapter progress with elapsed and remaining time, −15 s, play/pause, +15 s, speed and the chapter list. |
| **Sleep timer** | Stops playback after a duration or at the end of the chapter, fading out first. |
| **Catalogue entry** | A public-domain book offered for download in the Explore tab. |
| **OCR page** | A PDF page without a usable text layer whose text is recognized on-device. |
| **Page layout** | One page as PDF extraction or OCR sees it: its size and its layout lines in reading order (`PageLayout`). |
| **Outline entry** | A PDF bookmark: a title, the page it points to and optionally a position on that page (`OutlineEntry`). Marks where a chapter starts. |
| **Enclisis** | An unstressed pronoun joined to a verb with a hyphen ("fazê-lo", "dá-lhe"), or placed inside it (mesoclisis, "amá-lo-ei"). Cleanup keeps these hyphens when it joins words broken across lines. |
