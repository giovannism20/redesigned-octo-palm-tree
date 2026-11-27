# 0020. Bound catalogue downloads to about two minutes, and show and cancel them

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

[ADR 0017](0017-free-catalogue-from-project-gutenberg.md) (decision 3) retried
a download after a lost connection or a busy server with an exponential
backoff from 30 seconds, at most four attempts in all. While Project
Gutenberg's site was down for maintenance, the primary user tapped "Download"
and saw it "waiting" for more than five minutes, without knowing why and
without a way to stop it. [ADR 0019](0019-gutenberg-site-and-mirror-only.md)
adds the official mirror as a second source for downloads.

## Decision

1. **About two minutes, then an answer.** A download asks the site, then the
   mirror (ADR 0019). A failure that may pass (no connection, the site and
   the mirror answering 5xx or 429) is tried again after 15 seconds, within
   the same job, but no new try starts later than two minutes after the
   first one (`DownloadRetryPolicy`). Then the job fails with its reason,
   which Explore shows with "Try again". WorkManager no longer retries the
   job with a backoff.
2. **Say what is happening.** While it runs, the job publishes whether it is
   receiving the book from the site (with the percentage once the size is
   known), trying the alternative site, or waiting to try again, and Explore
   shows it ("Baixando…", "Tentando o site alternativo…", "O site não está
   respondendo. Tentando de novo em instantes…").
3. **Cancel.** A download that waits or runs has a "Cancel" button, which
   cancels its unique job; the book can then be downloaded again.
4. **Offline.** A download asked for without a connection still waits for
   one, as before, saying so, and can be cancelled.

This replaces the retries of ADR 0017's decision 3; the rest of that decision
stands.

## Alternatives considered

- **WorkManager's backoff with fewer attempts** — the waits between attempts
  grow and the listener cannot tell what is going on.
- **No automatic retry at all** — a short blip of the connection would fail
  the download at once.
- **A hard limit that also cuts a download under way** — a slow connection
  would then lose a book that is arriving; the limit stops new tries instead.

## Consequences

- Positive: the listener sees what the download does, can stop it, and gets
  an answer within about two minutes.
- Negative / costs: during a long outage the listener has to tap "Try again"
  later; a try that started just before the limit can end a little after it
  (connections time out after 15 seconds and reads after 30).
- Follow-ups: check on the primary user's phone that the statuses read well
  with TalkBack.
