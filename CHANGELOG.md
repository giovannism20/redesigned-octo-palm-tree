# Changelog

All notable user-visible changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Importing a file that failed before (for example a format added in a later
  version) prepares it again instead of saying it is already in the library.
- Follow-along text: "Text" in the player shows the chapter being read in
  large type with the sentence being spoken highlighted, and keeps it in
  view. Scroll freely and tap "Back to the reading" to return; tap any
  sentence to listen from there; A− and A+ change the text size (on top of
  the phone's font size), which is remembered.
- Sleep timer in the player: stop listening after 5, 10, 15, 30, 45 minutes
  or 1 hour of listening, or at the end of the chapter. The countdown shows
  on the player, the voice fades out gently over the last seconds, the place
  is saved, and the timer can be turned off. It works with the screen off.
- Time left: the library and "Continue listening" show how long the rest of
  each book takes at your speed ("Faltam 3 h 20 min"), and the player shows
  the time left in the chapter and in the book. How fast each voice speaks is
  remembered between sessions, so the estimates stay accurate.
- Listening: books are read aloud with the phone's voice and keep playing with
  the screen locked, controlled from the notification, the lock screen and
  headsets (−15 s, play/pause, +15 s; headset next/previous also move 15 s).
- Listening resumes at the start of the sentence where it stopped; the place
  is saved as you listen, pause or stop, and a finished book starts over.
  After a pause of more than five minutes (or a long phone call) it starts a
  sentence or about ten seconds earlier, so the thread is not lost.
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
  the cover. Password-protected and oversized PDFs get a clear message.
- Scanned PDFs are read with text recognition on the phone, without
  internet: pages that are pictures of text are recognized and become
  chapters and paragraphs like any other book. PDFs that mix typed and
  scanned pages keep their page order, and blank pages are left alone. When
  nothing readable can be recognized, or a scan is too long, a clear message
  says so.
- While a scanned book is being prepared, the library shows "Recognizing
  text: page X of Y" with a progress bar, and so does a notification when
  notifications are allowed. Preparation keeps going with the screen locked
  and, if the app is closed, continues later from the page where it
  stopped. Removing a book that is being prepared stops its preparation.
- Voices screen: the phone's free Portuguese voices, offline first, with a
  sample and a default choice.
- EPUB 2 and 3 books are imported with their chapters, titles, authors and
  cover; copy-protected (DRM) and damaged files get a clear message.
- Library with "Continue listening", progress, import failures and removal.
- Add books from the file picker or by sharing/opening them from other apps;
  duplicates are detected and files are verified by their content.
- Repository foundation: secret guard (pre-commit hook and CI scan),
  security policy, AI-first documentation and architecture decisions.
