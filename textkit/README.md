# textkit

Pure Kotlin/JVM library with R Reader's platform-independent text logic
([ADR 0004](../docs/decisions/0004-pure-jvm-textkit-module.md)). It must never
depend on Android APIs. Public APIs use Kotlin explicit API mode and carry
KDoc.

## Packages

| Package | Contents | Status |
|---------|----------|--------|
| `com.giovannimartins.rreader.textkit` | `BookFormat` detection from file signatures | implemented |
| `...textkit.model` | `NormalizedBook`, `Chapter`, `BookMetadata`, `CoverImage`, `TextRange`, `ReadingPosition` | implemented |
| `...textkit.epub` | `EpubParser`: EPUB 2/3 container, OPF metadata, spine, nav/NCX chapter titles, cover and DRM detection, with safe ZIP and XML handling | implemented |
| `...textkit.text` | `SentenceSegmenter` splits text into pt-BR aware sentence ranges (abbreviations, initials, dialogue dashes; never across paragraphs); `SegmentPlanner` groups them into rendered segments and finds the segment to resume from; `SpeechNormalizer.toSpeech` rewrites a segment or heading for voices at render time | implemented |
| `...textkit.layout` | `LayoutBookBuilder`: PDF/OCR layout lines → `NormalizedBook` (running headers, footers and page numbers removed, hyphenation joined keeping Portuguese enclisis, paragraphs rebuilt, chapters from outline, headings or page ranges); `ScannedDocumentDetector`; `DocumentInfoCleaner` | implemented |
| `...textkit.estimate` | `SpeakingRate` learns a voice's characters per second; `ReadingEstimates` derives progress, time left and character shifts (±15 s) across chapters from a `ReadingPosition` | implemented |

## EPUB parsing

```kotlin
when (val result = EpubParser().parse(file, fallbackTitle = file.nameWithoutExtension)) {
    is EpubResult.Success -> result.book // NormalizedBook: metadata, chapters, optional cover
    is EpubResult.Failure -> result.error // EpubError, mapped to a user-facing message
}
```

- `parse` never throws for bad input. Failures are typed: `NotAZipArchive`,
  `MissingContainer`, `MissingPackageDocument`, `DrmProtected`,
  `LimitExceeded(limit)`, `MalformedDocument(path)` and `NoReadableText`.
- `ArchiveLimits` caps the entry count and the decompressed bytes per entry
  and per parse (defaults: 10,000 entries, 32 MiB, 256 MiB), counting bytes
  actually inflated rather than declared sizes. Entries are read in memory
  by exact name, never written to disk, and XML goes through jsoup's parser,
  which loads no DTDs or external entities (SR-1 to SR-3 in
  [docs/security.md](../docs/security.md)).
- Chapters follow the spine, skipping `linear="no"` items. Titles come from
  the EPUB 3 `nav` (`epub:type="toc"`), else the EPUB 2 NCX; a file the table
  of contents does not point to joins the chapter before it. Without a usable
  table of contents, each file is a chapter titled by its first `h1`–`h3`.
- Chapter text keeps paragraphs and leaves out scripts, styles, navigation,
  ruby annotations and footnotes; whitespace is collapsed, soft hyphens and
  zero-width characters are removed and accents are composed (NFC).
- The cover comes from the EPUB 3 `cover-image` item or EPUB 2
  `<meta name="cover">`, images only.

## PDF and OCR layout

```kotlin
val pages: List<PageLayout> = ... // lines with their boxes and font sizes, from PdfBox or OCR
when (val result = LayoutBookBuilder().build(metadata, pages, outline)) {
    is LayoutResult.Success -> result.book // chapters of cleaned paragraphs
    is LayoutResult.Failure -> result.error // ScannedDocument or NoReadableText
}
```

- Coordinates are in page units with the origin at the top-left corner; a
  line's `bottom` is its baseline for PDF text. The rules assume one column.
- **Running lines.** Only lines entirely inside the top or bottom 10% of the
  page are candidates. A bare page number (Arabic or canonical Roman, with
  optional "página"/"p." and dashes, dots or brackets, as in `— 12 —`) is
  removed; so is a line whose text repeats in the same band at most two
  pages away, and a line that differs from such a neighbour only in digits
  when one of its numbers follows the page numbering (number minus page
  index shared by at least three pages). Chapter headings printed at the top
  of distant pages are therefore kept.
- **Scanned documents.** After that removal, a page with fewer than 20
  letters and digits has no usable text; more than half of such pages make
  the document scanned.
- **Paragraphs.** A line starts a paragraph when it starts with a dialogue
  dash after a sentence end, after a gap of more than 1.3 times the usual
  baseline spacing, when the font size changes by more than 20%, when it is
  indented by at least 0.75 em from the column (or centered), after a
  centered line, and after a line that ends a sentence and stops short of
  the column (5% of its width in justified text, 20% in ragged text). Column
  edges are measured per page; pages with fewer than three lines borrow
  them from the nearest page on the same side of the spread. Lines without
  letters or digits (`* * *`) end the paragraph and are dropped; a drop cap
  joins its word.
- **Hyphenation.** A word broken at a hyphen or soft hyphen is joined without
  it ("pala-vra"), but a hyphen that joins a verb and a pronoun is kept
  ("fazê-lo", "dá-lhe", "amá-lo-ei"). As syllables like "noi-te" or
  "capítu-lo" look the same, the hyphen is kept only when the fragment
  before it ends like a verb form (accented vowel, `m`, `z`, `ão`, `õe`,
  past-tense `ou`/`eu`/`iu` and similar rules listed on `Enclisis`). When
  the next line repeats the hyphen, as Portuguese typesetting does for words
  like "guarda-chuva" broken at their own hyphen, one hyphen is kept.
- **Chapters.** The outline first (a chapter starts at the first line left
  on the entry's page, or at its position), else headings (section words
  with numbers such as `CAPÍTULO IV`, `PARTE PRIMEIRA`, `Prólogo`, or short
  lines in a font at least 1.4 times the body size), else ten-page ranges
  without titles. A strategy needs two chapters, or one in a document of at
  most ten pages. Text before the first chapter start becomes an untitled
  chapter; chapters are never empty.
- `DocumentInfoCleaner` keeps a PDF's own title, authors and language only
  when meaningful: file names, "Microsoft Word - ..." exports, placeholders
  ("Untitled", "Documento1") and computer user names are ignored.

## Testing

```bash
./gradlew :textkit:test
```

Fixtures are generated in test code; see [docs/testing.md](../docs/testing.md).
