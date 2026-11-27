# 0017. Free catalogue from Project Gutenberg through an allowlisted HTTPS client

- **Status:** Accepted; the allowlisted hosts of decision 5 are replaced by
  [ADR 0019](0019-gutenberg-site-and-mirror-only.md) (exactly the site and its
  official mirror), and the retries of decision 3 by
  [ADR 0020](0020-bounded-catalogue-downloads.md) (about two minutes, with
  status and cancel)
- **Date:** 2026-11-27

## Context

Roadmap Phase 6 adds an Explore tab: curated public-domain classics in
Portuguese, a search, and downloads into the library. It is the first feature
that uses the network, which [ADR 0012](0012-no-accounts-local-only-data.md)
allows only after an explicit user action and over HTTPS (SR-5).

Facts about the source, checked in November 2026 (Gutenberg's site was down
for maintenance on the 27th, so through its official mirror
`gutenberg.pglaf.org` and the Internet Archive's November snapshots of its
pages and feeds):

- **Project Gutenberg** is a non-profit library of proofread public-domain
  ebooks with stable ebook numbers, among them several hundred books in
  Portuguese (Machado de Assis, José de Alencar, Eça de Queirós, Camilo
  Castelo Branco, Lima Barreto…), many in the spelling of their time.
- Its catalogue is an OPDS 1 (Atom) feed: searches at
  `https://www.gutenberg.org/ebooks/search.opds/?query=…` (25 books per page,
  a `rel="next"` link with `start_index`, `application/atom+xml`); `l.pt` in
  the query keeps books in Portuguese. A JSON OPDS 2 feed is in testing, and
  Gutenberg expects to retire the XML feeds in 2027.
- A book's EPUB 3 with images is `https://www.gutenberg.org/ebooks/{id}.epub3.images`,
  which redirects (302) to `/cache/epub/{id}/pg{id}-images-3.epub` on the same
  host, served as `application/epub+zip` (Dom Casmurro: 222 KB). The files are
  regenerated monthly; each starts with an English header up to
  `*** START OF THE PROJECT GUTENBERG EBOOK … ***` and ends with
  `*** END OF … ***` and the full license, in its own file.
- Gutenberg's terms ask OPDS apps for a User-Agent with a contact address,
  and for no more requests than a person with a browser makes: one page per
  search, the next page only when the user scrolls to it. Its `robots.txt`
  keeps crawlers out of `/ebooks/search`; downloading more than about 100
  books a day is for mirrors.
- Since [ADR 0015](0015-on-device-ocr-with-bundled-ml-kit.md) the merged
  manifest removes `INTERNET`, which ML Kit's telemetry library asks for, and
  that library's upload backend.

## Decision

1. **Source: Project Gutenberg only.** The curated list is a small asset,
   `app/src/main/assets/catalog/curated.json`: 26 well-known classics in
   Portuguese, each an ebook number checked against Gutenberg's catalogue
   (`pg_catalog.csv` of 2026-11-08: text, language `pt`), with the title and
   author as readers know them and a short description written for the app.
   Showing it needs no network.
2. **Requests only from the listener's actions.** A search makes one request
   for the first page (`query=<words> l.pt`), the next page is asked for only
   when the listener taps "More results", and a book is downloaded when the
   listener taps "Download". Search pages are kept in memory for 15 minutes
   (at most 20), never on disk. Nothing is fetched to show the screen: no
   covers, no prefetching, no update checks.
3. **Downloads in WorkManager, then the normal import.** `DownloadBookWorker`
   (one unique job per ebook, `NetworkType.CONNECTED`) fetches the EPUB 3 into
   a temporary file in private storage (at most 100 MB, `application/epub+zip`
   only) and hands it to `BookIntake.importFile`: signature check, 300 MB
   limit, SHA-256 duplicate check (an already imported book says so), book
   row and import job, then the EPUB parser with its limits. A book asked for
   offline waits for a connection; a lost connection or a busy server
   (HTTP 429, 5xx) is retried with an exponential backoff from 30 seconds, at
   most four attempts in all. The temporary file is always deleted.
4. **The license is not read aloud.** textkit's `GutenbergBoilerplate` removes
   the header, the production credits right after it and the license from
   the chapters of any EPUB that has the markers. The stored source file keeps
   them: the book on the phone is Gutenberg's file, unchanged and not
   redistributed.
5. **One HTTP client, allowlisted twice (SR-5).**
   - In code, `CatalogHttp` is the app's only network client. Every address
     and every redirect target (at most 5) must be HTTPS on the default port,
     without user information, on `gutenberg.org` or a subdomain
     (`CatalogHosts`), or nothing is sent. Only a 200 answer of an expected
     media type is read; a size limit is checked against `Content-Length` and
     while reading; a body shorter than announced is a broken connection.
     Connecting times out after 15 s and each read after 30 s.
   - In the platform, `res/xml/network_security_config.xml` disables
     cleartext everywhere and trusts no certificate authority except for
     `gutenberg.org` and its subdomains, so a TLS connection to any other host
     fails, whatever code or library opens it.
6. **Platform HTTP stack.** `HttpsURLConnection` behind an `HttpTransport`
   interface (a fake in tests), with redirects followed by `CatalogHttp`, no
   response cache and no `CookieHandler`, so no cookies. No HTTP library is
   added.
7. **What leaves the phone.** Requests carry only a `User-Agent` and an
   `Accept` header. The agent is `RReader/<version> (Android book reader)`;
   when `secrets.properties` sets `CATALOG_CONTACT` (a web page or e-mail
   address, through `BuildConfig`) it is appended as `; +<contact>`, as
   Gutenberg asks. That value is not secret, but it identifies the
   maintainer, so it stays out of the public repository. Addresses, which
   hold the search words, are never logged: failures are recorded by
   exception type only (SR-8).
8. **Permissions.** `INTERNET` is declared, and `ACCESS_NETWORK_STATE`, so the
   Explore tab can say the phone is offline and WorkManager can wait for a
   connection. ML Kit's `datatransport` upload backend stays removed (ADR
   0015), and its host would fail TLS anyway; `MergedManifestTest` checks
   both. This replaces the removal of `INTERNET` in ADR 0015's first decision.

## Alternatives considered

- **Other public-domain libraries** (Brazil's Domínio Público portal,
  Wikisource, the Internet Archive) — no catalogue feed, mostly PDF scans, or
  pages that would have to be converted into books; Gutenberg offers
  proofread EPUBs with stable numbers and an OPDS feed.
- **OPDS 2 (JSON)** — still in testing and needs Gutenberg's approval; to
  revisit before the XML feeds are retired.
- **Gutenberg's catalogue files in the app** (CSV or RDF) — offline search,
  but 21 MB to 127 MB, stale within weeks, and searching them well is a
  project of its own.
- **OkHttp** — a nicer API for about a megabyte of code; two kinds of GET do
  not need it, and the platform client is built on it.
- **The platform's redirect following** — it changes host without asking, so
  each hop could not be checked against the allowlist.
- **Allowlisting in code only, or in the network security config only** —
  the code check gives precise failures and covers redirects before any
  connection; the platform check also stops libraries and future code.
- **`.epub.noimages` (EPUB 2 without images)** — smaller, but without the
  cover the library shows.
- **Cover thumbnails in the Explore list** — one more request per book shown,
  for little help to a listener.
- **The repository's address as the contact** — puts the maintainer's
  account in the public repository and in every request without their
  choice.

## Consequences

- Positive: free classics in a few taps, through the same import and limits
  as any file; one place to review for every network request; libraries
  cannot reach other hosts over TLS.
- Negative / costs: Gutenberg sees the phone's IP address, the search words
  and which books are downloaded; Gutenberg's EPUBs arrive split into two to
  four large files, so their books have few chapters until EPUB chapters are
  split at table-of-contents anchors (roadmap Phase 1); old spellings are read
  as written; downloads use mobile data when there is no Wi-Fi (books are
  usually under 1 MB).
- Follow-ups: set `CATALOG_CONTACT` before building a release; move to OPDS 2
  or another feed before 2027; voice model downloads (Phase 5) need their host
  allowed in code and in the network security config, with SHA-256 pinning
  (SR-6); check the endpoints again on the live site once it is back.
