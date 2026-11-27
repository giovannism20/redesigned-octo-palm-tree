# 0005. PDF text via PdfBox, scanned pages via ML Kit OCR

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

PDFs are layout documents: text comes as positioned lines with running
headers, footers, page numbers and hyphenation. Some PDFs are scans with no
text layer at all. Android's `PdfRenderer` renders pages but its text
extraction is only available on recent Android versions.

## Decision

- Extract text-layer PDFs with **PdfBox-Android** (Apache 2.0), keeping each
  line's page, position and font size.
- Render pages with Android's **`PdfRenderer`** for covers and for OCR input.
- Recognize scanned pages on-device with **ML Kit Text Recognition v2**
  (bundled Latin model, free, offline).
- Both extractors emit the same **layout lines**, which `:textkit` cleans
  (headers/footers, page numbers, hyphenation with Portuguese enclisis,
  paragraphs) and splits into chapters (PDF outline → detected headings →
  page ranges).
- Decide per page: pages without a usable text layer go through OCR, so mixed
  PDFs work.

## Alternatives considered

- **MuPDF** — excellent extraction and rendering, but AGPL licensing and a
  larger native footprint.
- **`PdfRenderer` text APIs** — not available on the minimum SDK.
- **Cloud OCR** — conflicts with [ADR 0012](0012-no-accounts-local-only-data.md).

## Consequences

- Positive: free, offline, one cleanup pipeline for both sources.
- Negative: PdfBox-Android is large and memory-hungry on huge files (mitigated
  by limits, SR-4); layout heuristics will not be perfect for multi-column or
  footnote-heavy books.
