# textkit

Pure Kotlin/JVM library with R Reader's platform-independent text logic
([ADR 0004](../docs/decisions/0004-pure-jvm-textkit-module.md)). It must never
depend on Android APIs. Public APIs use Kotlin explicit API mode and carry
KDoc.

## Packages

| Package | Contents | Status |
|---------|----------|--------|
| `com.giovannimartins.rreader.textkit` | `BookFormat` detection from file signatures | implemented |
| `...textkit.model` | `NormalizedBook`, `Chapter` (with `Chapter.paragraphRangesOf` for any chapter text), `BookMetadata`, `CoverImage`, `TextRange`, `ReadingPosition`; `MetadataLimits` bounds titles, authors, languages and descriptions | implemented |
| `...textkit.epub` | `EpubParser`: EPUB 2/3 container, OPF metadata, spine, chapters from the nav/NCX table of contents (whole files or the anchors its entries point to inside them, nested levels chosen by length, chapter headings in overlong sections, short sections joined), cover and DRM detection, with safe ZIP and XML handling | implemented |
| `...textkit.text` | `SentenceSegmenter` splits text into pt-BR aware sentence ranges (abbreviations, initials, dialogue dashes; never across paragraphs); `SegmentPlanner` groups them into rendered segments and finds the segment to resume from; `SpeechNormalizer.toSpeech` rewrites a segment or heading for voices at render time | implemented |
| `...textkit.layout` | `LayoutBookBuilder`: PDF/OCR layout lines → `NormalizedBook` (running headers, footers and page numbers removed, hyphenation joined keeping Portuguese enclisis, paragraphs rebuilt, chapters from outline, headings or page ranges); `OcrPageLayout`: OCR boxes in pixels → layout lines in page units with estimated font sizes; `ScannedDocumentDetector`: pages without a usable text layer, scanned documents; `DocumentInfoCleaner` | implemented |
| `...textkit.gutenberg` | Project Gutenberg: `GutenbergBoilerplate` removes the English header, production credits and license Gutenberg adds around a book (applied by `EpubParser`); `GutenbergFeedParser` reads its OPDS catalogue feeds (search results) into `FeedPage`s of `FeedBook`s | implemented |
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
  `LimitExceeded(limit)`, `MalformedDocument(path)`, `NoReadableText` and
  `TooComplex`. It throws `InterruptedException` when its thread is
  interrupted, which is how a caller cancels it.
- `ArchiveLimits` caps the entry count and the decompressed bytes per entry
  and per parse (defaults: 10,000 entries, 16 MiB, 256 MiB), counting bytes
  actually inflated rather than declared sizes, the text extracted from the
  spine (20 million characters, then `LimitExceeded(MAX_TEXT_CHARS)`) and
  the time a parse may take (two minutes, then `TooComplex`). Entries are read in memory by exact
  name, never written to disk, and XML goes through jsoup's parser, which
  loads no DTDs or external entities (SR-1 to SR-3 in
  [docs/security.md](../docs/security.md)).
- Chapters come from the table of contents, down to the elements its entries
  point to inside files, and long sections are split at their chapter
  headings (see [EPUB chapters](#epub-chapters)).
- Chapter text keeps paragraphs and leaves out scripts, styles, navigation,
  ruby annotations and footnotes; whitespace is collapsed, soft hyphens and
  zero-width characters are removed and accents are composed (NFC).
- The cover comes from the EPUB 3 `cover-image` item or EPUB 2
  `<meta name="cover">`, images only.
- Metadata and chapter titles stay within `MetadataLimits` (SR-13): titles
  are cut to 300 characters, authors to 20 names of at most 300 characters,
  descriptions to 4,000 characters, and a language that is not a BCP 47-like
  tag of at most 35 characters is left out. `Chapter.fromParagraphs`, which
  every parser uses, cuts chapter titles, and `DocumentInfoCleaner` applies
  the same limits to a PDF's authors and language.
- Project Gutenberg's header and license are left out of the chapters (see
  below).

## EPUB chapters

Chapters follow the spine, skipping `linear="no"` items, and the first table
of contents that points to a spine file: the EPUB 3 `nav`
(`epub:type="toc"`), else the EPUB 2 NCX. `ChapterAssembler` applies these
rules:

- **Files and anchors.** An entry for a whole file starts a chapter at the
  file's first paragraph, and files the table of contents does not point to
  join the chapter before them. An entry for an element
  (`chapter.xhtml#chap02`) starts a chapter at that element, so books that
  keep many chapters in a few large files, as Project Gutenberg's do, get
  their real chapters. On an inline or empty element the chapter starts at
  the paragraph the element opens, or at the next one when text comes before
  it. Text before the first anchor of a file stays with the chapter before
  it, and an entry whose element the file lacks points to the start of the
  file.
- **Headings before an anchor.** The headings right before an anchored
  element join its chapter when they follow body text or open the file, so a
  number printed above the title (`<h5>II</h5><h4 id="c2">Do livro.</h4>`)
  starts chapter II instead of ending chapter I.
- **Nested tables of contents.** Going down from the top level, the next
  level of anchors is used when the current one is too coarse (one section,
  or the sections longer than 50,000 characters, about an hour of listening,
  hold half of the text) or when the sections the next level adds are at
  least 3,000 characters (about 3.5 minutes) long in the median. So chapters
  nested under parts or under a title-page entry are used even when they are
  short, while numbered sub-sections inside chapters, title-page lines and
  "FIM" entries nested below chapters are not. Entries for whole files
  always count, and only the first eight levels are examined.
- **Long sections.** A section longer than 50,000 characters, as in a book
  whose table of contents lists only the title page, the parts or the
  license, is split at its chapter headings when it holds at least two:
  `h1`–`h6` headings worded like `CAPÍTULO I`, `CAPITULO PRIMEIRO`,
  `PARTE II` or `Prólogo`, or a chapter number alone or before a title (`I`,
  `XII.`, `12`, `IV — A partida`), and Project Gutenberg's
  `<div class="chapter">` or EPUB 3 `epub:type="chapter"` divisions. Failing
  that, it is split at its `h1`–`h3` headings, again when it has two.
- **Titles.** A chapter is titled by its table-of-contents label, else by the
  heading that started it, else by its first `h1`–`h3` heading. Text before
  the first start forms a leading chapter, and chapters without text are
  dropped. Without a table of contents that points to a spine file, each file
  starts a chapter.
- **Short chapters.** Once Project Gutenberg's header and license are removed
  (below), a chapter shorter than 300 characters, such as title-page lines, a
  part title or a closing "FIM", joins the next chapter when that one starts
  in the same file, else the chapter before it in the same file. The joined
  chapter keeps the title of the part that is not short, or of the first part
  when both are. Chapters never join across files, so a book with one file
  per chapter keeps every chapter.

## Project Gutenberg books

Gutenberg wraps every book in an English header and license.
`GutenbergBoilerplate.strip(chapters)`, which `EpubParser` applies, removes
them so they are not read aloud:

- everything up to the `*** START OF THE PROJECT GUTENBERG EBOOK … ***` line,
  looked for in the first 100 paragraphs, and up to three production credits
  right after it ("Produced by …", "… Distributed Proofreading Team at
  https://www.pgdp.net");
- everything from the `*** END OF THE PROJECT GUTENBERG EBOOK … ***` line,
  or from an "End of the Project Gutenberg EBook of …" line before it.

The markers are matched in the spellings Gutenberg used over the years
(`THE`/`THIS`, `EBOOK`/`E-BOOK`/`ETEXT`, any letter case), also inside a
longer paragraph, as in old preformatted headers, whose remaining text is
kept. Paragraphs are only removed, never rewritten. Chapters left empty are
dropped, a chapter title naming Project Gutenberg is cleared, and a book
without markers, or one that would be left without text, is returned
unchanged.

## Project Gutenberg catalogue feeds

```kotlin
val page: FeedPage? = GutenbergFeedParser.parse(xml) // null when the text is not an Atom feed
page?.books // FeedBook(id = 55752, title = "Dom Casmurro", author = "Machado de Assis"), ...
page?.next  // "/ebooks/search.opds/?query=...&start_index=26", or null on the last page
```

- Reads Gutenberg's OPDS 1 (Atom) feeds, such as search results. An entry is
  a book when its `subsection` link or its `id` points to
  `/ebooks/{number}.opds`; the author comes from `author/name`, else from a
  plain-text `content`, as search results give it. Other entries (authors,
  subjects, sort orders), repeated books, entries without a title and those
  after the first 100 are left out; text is cleaned like book text.
- The feed is untrusted network input: it goes through the same jsoup XML
  parser as EPUB content (no DTDs, external entities or entity expansion,
  SR-3), and `parse` never throws. The caller decodes the bytes and limits
  their size.

## PDF and OCR layout

```kotlin
val pages: List<PageLayout> = ... // lines with their boxes and font sizes, from PdfBox
val needOcr: List<Int> = ScannedDocumentDetector.pagesWithoutText(pages)
val ocrPage = OcrPageLayout.build(index, width, height, pixelsPerUnit, blocks) // what OCR read on a page picture
when (val result = LayoutBookBuilder().build(metadata, pages, outline)) {
    is LayoutResult.Success -> result.book // chapters of cleaned paragraphs
    is LayoutResult.Failure -> result.error // ScannedDocument or NoReadableText
}
```

- Coordinates are in page units (PDF points) with the origin at the top-left
  corner; a line's `bottom` is its baseline for PDF text and the bottom of
  its box for OCR. The rules assume one column.
- **OCR pages.** `OcrPageLayout` divides the boxes a recognizer reports by
  the scale of the page picture and clips them to the page, dropping lines
  without text or area, in the recognizer's order. OCR boxes span only the
  glyphs drawn, so the font size is the box height for a line with
  lower-case letters (ascenders to descenders) and the height divided by 0.7
  for a line of capitals or digits; every line takes at least the median
  size of its block, so a short line without ascenders or descenders stays
  in its paragraph. OCR pages and text-layer pages can be mixed.
- **Running lines.** Only lines entirely inside the top or bottom 10% of the
  page are candidates. A bare page number (Arabic or canonical Roman, with
  optional "página"/"p." and dashes, dots or brackets, as in `— 12 —`) is
  removed; so is a line whose text repeats in the same band at most two
  pages away, and a line that differs from such a neighbour only in digits
  when one of its numbers follows the page numbering (number minus page
  index shared by at least three pages). Chapter headings printed at the top
  of distant pages are therefore kept.
- **Scanned documents.** After that removal, a page with fewer than 20
  letters and digits has no usable text (`pagesWithoutText` lists them for
  OCR); more than half of such pages make the document scanned, which after
  OCR means that no readable text could be recognized.
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
