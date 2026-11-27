# textkit

Pure Kotlin/JVM library with R Reader's platform-independent text logic
([ADR 0004](../docs/decisions/0004-pure-jvm-textkit-module.md)). It must never
depend on Android APIs. Public APIs use Kotlin explicit API mode and carry
KDoc.

## Packages

| Package | Contents | Status |
|---------|----------|--------|
| `com.giovannimartins.rreader.textkit` | `BookFormat` detection from file signatures | implemented |
| `...textkit.epub` | EPUB container/OPF/spine/TOC parsing with safe ZIP and XML handling | planned (Phase 1) |
| `...textkit.text` | Sentence segmentation, speech normalization | planned (Phase 1) |
| `...textkit.layout` | Cleanup of PDF/OCR layout lines, chapter detection | planned (Phase 2) |
| `...textkit.estimate` | Progress and time-left estimates | planned (Phase 1) |

## Testing

```bash
./gradlew :textkit:test
```

Fixtures are generated in test code; see [docs/testing.md](../docs/testing.md).
