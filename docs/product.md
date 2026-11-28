# Product

## Problem

Long books are hard to read for people with tired eyes or little free time,
but many of them happily listen for hours. Commercial read-aloud apps charge
subscriptions or cap free listening time. R Reader turns any PDF or EPUB the
user already has into a listening experience, for free and offline.

## Primary user

An older adult who:

- speaks Portuguese and listens to long books (classics, novels, history);
- uses an Android phone (often a Samsung) and is not confident with
  technology: receives books as files in messaging apps and opens them from
  there;
- listens for long sessions with the screen locked, sometimes with a
  Bluetooth headset, sometimes falling asleep while listening;
- may have reduced vision, so text and buttons must be large.

Every design decision is checked against this person first.

## Jobs to be done

1. **Add a book** received in a messaging app or saved in the phone's files.
2. **Press play and listen** with a pleasant Portuguese voice, with the
   screen locked, for hours.
3. **Continue where I stopped**, even days later, even after the phone
   restarted.
4. **Control playback without opening the app**: lock screen, notification,
   headset buttons.
5. **Go back a little** when distracted (−15 s), or jump to a chapter.
6. **Fall asleep** without losing the place (sleep timer).
7. **Read along** with large text that follows the voice.
8. **Find something new to read** for free (public-domain classics).

## Version 1 scope

| Area | Included |
|------|----------|
| Formats | EPUB 2/3 (without DRM), PDF with a text layer, scanned PDF via on-device OCR |
| Import | System file picker, "Share to" / "Open with" from other apps |
| Library | Cover, title, author, progress %, estimated time left, two parts: "Ouvindo" (books in progress, each with Listen/Pause) and "Na biblioteca" (the others), search, edit title/author, archive, remove |
| Player | Background playback, lock-screen/notification/headset controls, ±15 s, chapters, speed 0.75–2×, resume, small rewind after a long pause |
| Reading | Follow-along text with the current sentence highlighted, tap a sentence to read from there, adjustable text size, a choice of reading font (Atkinson Hyperlegible or the serif Literata) |
| Ajustes | Text size for every screen on top of the phone's (four steps), dark or light theme or the phone's choice, the reading font, with a live preview |
| Sleep timer | Minutes or end of chapter, with a gentle fade-out |
| Voices | Android system voices (offline preferred); later, downloadable on-device neural voices |
| Catalogue | Curated and searchable public-domain books in Portuguese from Project Gutenberg, downloaded into the library |

## Non-goals

- Accounts, login, cloud sync or any server operated by this project.
- Paid or cloud text-to-speech services.
- Analytics, telemetry, ads or tracking of any kind.
- DRM-protected books (the app explains why they cannot be opened).
- Imitating real people's voices or other apps' branding.
- iOS (for now).

## UX principles

1. **One obvious action per screen.** The play button is the hero.
2. **Large and forgiving.** Body text ≥ 18 sp, primary buttons ≥ 56 dp,
   destructive actions confirmed, nothing important hidden in gestures.
3. **Honour the system, then the listener.** Respect the phone's font scale
   and let the listener make all text larger on top of it; dark theme by
   default with high contrast, and a light theme (or the phone's choice) when
   the listener prefers it; TalkBack labels everywhere.
4. **Never lose the place.** Progress is saved continuously; resuming is
   always one tap.
5. **Plain language.** Short Portuguese copy, no jargon, errors that say what
   to do next.
6. **Offline first.** Everything works in airplane mode except explicit
   downloads.

## Success criteria

- The primary user imports a book from a messaging app and starts listening
  without help.
- Playback runs for 2 hours with the screen locked without stopping.
- After force-closing the app, playback resumes within one sentence of where
  it stopped.
- No crash or freeze on malformed files; the user sees a clear message.
