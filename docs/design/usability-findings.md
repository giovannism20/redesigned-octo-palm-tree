# Usability findings

Dated findings from sessions run with [usability-test.md](usability-test.md).
No names or personal details.

## 2026-11-27 — owner, Galaxy S24+ (Android 17 / One UI 9), debug build

| # | Task | What happened | Severity | Fix |
|---|------|---------------|----------|-----|
| 1 | Download a free classic | Downloads never finished: the catalogue site was down for maintenance (HTTP 503) and the job retried silently. | Blocked | Fixed: fall back to the official mirror `gutenberg.pglaf.org`; a clear "site under maintenance" message ([ADR 0019](../decisions/0019-gutenberg-site-and-mirror-only.md)). |
| 2 | Choose a voice | Voice cards had no picture and the phone's voices looked identical ("Voz N · Brasil"). | Slowed down | Voices tab redesign (Phase 5): an emblem and a name per voice, on-screen samples, one "phone voice" card with the system voices collapsed. |
| 3 | Add a book from the picker | The system storage provider stopped responding once (`ContentProvider not responding` in `com.android.externalstorage`); the app itself did not crash. | Slowed down | System-side; keep the "Share → R Reader" path documented and retry; watch for repeats. |
| 4 | Listen | In the player, some text, the times and the 15-second icons were black on the dark background, and the speed, timer, chapters and text buttons were below the screen edge. | Slowed down | Fixed: shared `ScreenSurface` for screens outside the scaffold, a player that fits one screen, regression tests (readable text, everything on screen). |
| 5 | Change the voice while listening | Not possible from the player. | Slowed down | Phase 5 work: a "Voice" shortcut in the player and voice changes applied at the current sentence. |
| 6 | Download a free classic | It said "waiting for the internet" although the phone was online and the site was down for maintenance, and kept trying for minutes. | Slowed down | Mirror fallback, about two minutes per attempt, honest status, Cancel and Try again ([ADR 0019](../decisions/0019-gutenberg-site-and-mirror-only.md), [ADR 0020](../decisions/0020-bounded-catalogue-downloads.md)). |
| 7 | Consistency | Cards, the active tab and progress bars used default greys and purples; the owner asked for one standard of colours and type on every screen. | Cosmetic | Every colour role mapped to the brand, one typeface designed for low vision, full type scale, guarded by `ThemeTest`. |
| 8 | Minimize the player | The owner expected to swipe the player down, besides the minimize button. | Suggestion | Added: swipe down to minimize (springs back when the pull is short and slow). |

What worked: playback went on with the screen locked (Android 17 background
audio rules), and the full player opens and resumes where it stopped.

Automated checks on the same phone: 10/10 instrumented tests pass (playback
service under Android 17 audio rules, PDF cover rendering, ML Kit OCR).
