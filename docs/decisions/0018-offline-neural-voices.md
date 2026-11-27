# 0018. Offline neural voices with sherpa-onnx; GPL-3.0 applies to the APK

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

The voice evaluation ([docs/design/voices-evaluation.md](../design/voices-evaluation.md))
compared nine free on-device voices (Kokoro pf_dora, pm_alex, pm_santa; Piper
cadu, faber, jeff, dii, miro, edresson) rendered with sherpa-onnx 2.0.3.
Portuguese pronunciation needs the `piper-phonemize` package (espeak-ng data),
which is licensed **GPL-3.0**. Kokoro is Apache-2.0; the Piper pt-BR voices
carry non-commercial or research-only terms on their training data, which a
personal, free app can honour.

## Decision

- Offer **all nine voices** in the Voices tab, ordered by recommendation, next
  to the phone's own voices. Models are downloaded on demand from the official
  sherpa-onnx releases over HTTPS, verified against SHA-256 values pinned in
  code (SR-6), and can be deleted to free space.
- Each voice is presented with its **own small emblem** (an abstract mark, never
  a portrait or a real person's likeness), its name and a **short sample that
  plays on screen before downloading** (short samples ship inside the app).
  Licence terms are **not** shown on the voice cards; they live in an
  "About and licences" screen.
- Accept GPL-3.0 **for the APK only**: the source code in this repository stays
  MIT, but the distributed app, which links the GPL-3.0 phonemizer, is subject
  to GPL-3.0. The app therefore ships an "About and licences" screen with the
  licence texts (GPL-3.0, Apache-2.0, MIT, each voice model's terms) and a link
  to this public repository (the corresponding source), and the README states
  it.
- Kokoro voices render ahead of playback (they are about real time or slower
  on a phone); Piper voices can render just ahead like the system voice.

## Alternatives considered

- **System voices only** — no licence questions, but noticeably less natural.
- **Relicensing the whole repository to GPL-3.0** — equivalent in practice;
  the owner preferred keeping the repository MIT.
- **A custom pronunciation lexicon without espeak-ng** — large effort and risk.

## Consequences

- Positive: free, offline, more natural voices for the primary user.
- Negative: the APK cannot be distributed as closed source while it includes
  the phonemizer; the app grows with the phonemizer data (mitigated by
  packaging only the Portuguese data if possible) and downloads 67–132 MB per
  voice package.
- Follow-ups: implement in roadmap Phase 5; record each model's licence in
  `THIRD_PARTY_NOTICES.md` and in the in-app licences screen.
