# Usability findings

Dated findings from sessions run with [usability-test.md](usability-test.md).
No names or personal details.

## 2026-11-27 — owner, Galaxy S24+ (Android 17 / One UI 9), debug build

| # | Task | What happened | Severity | Fix |
|---|------|---------------|----------|-----|
| 1 | Download a free classic | Downloads never finished: the catalogue site was down for maintenance (HTTP 503) and the job retried silently. | Blocked | Fall back to the official mirror `gutenberg.pglaf.org`; show a clear "site under maintenance" message after retries (security/network work). |
| 2 | Choose a voice | Voice cards had no picture and the phone's voices looked identical ("Voz N · Brasil"). | Slowed down | Voices tab redesign (Phase 5): an emblem and a name per voice, on-screen samples, one "phone voice" card with the system voices collapsed. |
| 3 | Add a book from the picker | The system storage provider stopped responding once (`ContentProvider not responding` in `com.android.externalstorage`); the app itself did not crash. | Slowed down | System-side; keep the "Share → R Reader" path documented and retry; watch for repeats. |

Automated checks on the same phone: 10/10 instrumented tests pass (playback
service under Android 17 audio rules, PDF cover rendering, ML Kit OCR).
