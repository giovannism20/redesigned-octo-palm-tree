# Changelog

All notable user-visible changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Listening: books are read aloud with the phone's voice and keep playing with
  the screen locked, controlled from the notification, the lock screen and
  headsets (−15 s, play/pause, +15 s; headset next/previous also move 15 s).
- Listening resumes at the start of the sentence where it stopped; the place
  is saved as you listen, pause or stop, and a finished book starts over.
- Player screen with cover, chapter progress and time left, large −15 s,
  play/pause and +15 s buttons, speed from 0.75× to 2× and a chapter list.
- Mini player above the navigation bar while a book is open; tapping a book
  in the library (or "Listen" on "Continue listening") starts it.
- Clear messages when no Portuguese voice is installed (with a shortcut to
  Voices) or when the voice fails, with a way to try again.
- PDF books with a text layer are imported: chapters come from the
  bookmarks, chapter headings or page ranges; running headers, footers and
  page numbers are left out, paragraphs are rebuilt and words broken across
  lines are joined, keeping forms such as "fazê-lo"; the first page becomes
  the cover. Scanned, password-protected and oversized PDFs get a clear
  message.
- Voices screen: the phone's free Portuguese voices, offline first, with a
  sample and a default choice.
- EPUB 2 and 3 books are imported with their chapters, titles, authors and
  cover; copy-protected (DRM) and damaged files get a clear message.
- Library with "Continue listening", progress, import failures and removal.
- Add books from the file picker or by sharing/opening them from other apps;
  duplicates are detected and files are verified by their content.
- Repository foundation: secret guard (pre-commit hook and CI scan),
  security policy, AI-first documentation and architecture decisions.
