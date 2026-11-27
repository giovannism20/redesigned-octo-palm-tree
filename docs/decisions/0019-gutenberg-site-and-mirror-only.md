# 0019. Allowlist exactly Gutenberg's site and its official mirror; download from the mirror when the site is down

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

[ADR 0017](0017-free-catalogue-from-project-gutenberg.md) allowed `gutenberg.org`
and all its subdomains, in code (`CatalogHosts`) and in the network security
config. The catalogue only ever uses `www.gutenberg.org`: searches, and book
downloads, which redirect to `/cache/epub/` on the same host. A security audit
of November 2026 recommended allowing exactly the hosts in use.

On 27 November 2026 `www.gutenberg.org` was down for maintenance: searches and
downloads answered HTTP 503, and a book asked for kept being retried in the
background for minutes. Project Gutenberg's official mirror,
`gutenberg.pglaf.org`, run by the Project Gutenberg Literary Archive
Foundation, served the same file at `/cache/epub/{id}/pg{id}-images-3.epub`
(200, `application/octet-stream`).

## Decision

1. **Two hosts, exactly.** The app may contact `www.gutenberg.org` (searches
   and downloads) and `gutenberg.pglaf.org` (downloads), by those names only:
   no other subdomain, not the bare `gutenberg.org`. `CatalogHosts.HOSTS` is
   checked for every address and redirect, and the network security config
   trusts the system's authorities for these two names only
   (`includeSubdomains="false"`). This replaces the allowlist of ADR 0017's
   decision 5; the rest of that decision stands.
2. **The mirror as a fallback for downloads.** A download asks the site
   first. When the site answers HTTP 5xx or 429, or cannot be reached, the
   same EPUB is asked from the mirror. When both fail, the site's failure is
   the one reported. Searches use the site only.
3. **Media types.** An EPUB download accepts `application/epub+zip` and
   `application/octet-stream`, which the mirror sends; the intake checks the
   EPUB signature of whatever arrives, as for any file.
4. **Saying the site is down.** A search or download that ends with the site
   answering HTTP 5xx or 429 tells the listener that the free books site is
   under maintenance and to try again later.

## Alternatives considered

- **Keep `gutenberg.org` and every subdomain** — simpler to state, but it
  trusts hosts the app never needs.
- **Other mirrors** (`aleph.gutenberg.org`, university mirrors) — more ways
  to get a book, each another host to trust and to check; the foundation's
  own mirror is enough as a fallback.
- **Searching on the mirror too** — the mirror serves files, not the OPDS
  search.

## Consequences

- Positive: two hosts to trust instead of a whole domain; books still
  download while the site is under maintenance; the listener is told why a
  search or a download does not work.
- Negative / costs: if Gutenberg moves its downloads to another host, or the
  mirror changes its layout, downloads fail as "unavailable" until the app is
  updated; the mirror also sees the phone's IP address and the books
  downloaded from it.
- Follow-ups: check both hosts again when Gutenberg moves to its OPDS 2 feed.
