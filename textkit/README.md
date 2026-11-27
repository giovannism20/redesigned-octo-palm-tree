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
| `...textkit.layout` | Cleanup of PDF/OCR layout lines, chapter detection | planned (Phase 2) |
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

## Testing

```bash
./gradlew :textkit:test
```

Fixtures are generated in test code; see [docs/testing.md](../docs/testing.md).
