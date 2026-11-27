# Changelog

All notable user-visible changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Swipe down on the player to minimize it, besides its minimize button.
- New app icon "Livro Aberto" (an open book formed by two R's with a
  bookmark ribbon), with a themed version for Android 13+, a launch screen and
  the app's colours taken from it.
- Explore tab: free classics in Portuguese from Project Gutenberg, such as
  Dom Casmurro, Iracema and Os Maias, each with a short description, and a
  search by title or author. One tap on "Download" adds a book to the
  library; the download goes on when you leave the screen, waits for the
  internet when the phone is offline, and says when the book is already in
  the library. Without internet the screen says so, and the suggestions stay
  visible.
- Books from Project Gutenberg are read without the English license text
  that comes with them: listening starts with the book itself and stops at
  its last page.
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

### Changed

- One look on every screen: a typeface designed for readers with low vision
  (Atkinson Hyperlegible Next) throughout the app, and the app's own colours
  on cards, tabs, progress bars, the chapter list and messages instead of
  default greys and purples.
- The message for a book that is too complex to prepare no longer says
  "PDF", since EPUB books can get it too.
- EPUB books that keep many chapters in a few large files, as most Project
  Gutenberg books in the Explore tab do, now have their real chapters, each
  with its title, in the chapter list, for next and previous and for the time
  left in the chapter: Dom Casmurro has its 148 chapters instead of 3. A
  chapter number printed above the title stays with its chapter, title pages
  and part titles are read at the start of the chapter that follows them, and
  books whose table of contents lists only the title page or the parts are
  split at their "CAPÍTULO I" headings. Books already in the library keep
  their chapters until they are removed and added again.

### Fixed

- A backup started by the phone (for example at night while charging) no
  longer stops the book being read: R Reader takes no part in Android backup.
- The player and the follow-along text showed some text, the times and the
  15-second buttons in black on the dark background, and the player's speed,
  timer, chapters and text buttons were below the edge of the screen. Both
  screens now use the app's colours, and the player fits on one screen
  (it scrolls only with very large system text).
- A book whose preparation failed in an unexpected way, or kept stopping, no
  longer stays "Preparing the book…" forever: it says why it could not be
  prepared, and adding the same file again tries again.
- A PDF whose cover or one of whose scanned pages makes the phone's PDF
  reader crash no longer makes the app close again and again while the book
  is prepared: after the second time, that cover is left out or that page is
  skipped.
- Sharing a book from an app that misbehaves no longer closes R Reader: it
  says the book could not be saved. The same book shared twice at the same
  time is added once.
- A catalogue book with an extremely long title can be downloaded.
- While Project Gutenberg's site is down for maintenance, catalogue books
  are downloaded from its official mirror, and a search says that the free
  books site is under maintenance instead of only "not working".
- A catalogue download no longer keeps trying for many minutes without a
  word: it shows "Downloading…", "Trying the alternative site…" or that it
  will try again in a moment, can be cancelled, and after about two minutes
  says why it could not finish, with "Try again".

### Security

- A book with an enormous title, list of authors or description, or shared
  under an enormous file name, can no longer make the app close every time it
  opens: such values are cut to a sensible length when the book is added, and
  if the library ever cannot be read, it says so and offers "Try again".
- Other apps can no longer control listening (play, pause, skip, the sleep
  timer or "read from here") by sending commands to the app's playback
  service; the app itself, the notification, the lock screen and headsets
  still can.
- An EPUB built to keep its preparation busy for hours now stops after two
  minutes with a clear message.
- The catalogue contacts exactly two hosts, Project Gutenberg's site and its
  official mirror, instead of any address under gutenberg.org.
