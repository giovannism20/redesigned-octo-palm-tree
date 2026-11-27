# Offline neural voices: evaluation (2026-11-27)

Input for roadmap Phase 5 (ADR 0002: free, on-device voices only). Samples of
the same Portuguese passage were rendered on the development PC with
sherpa-onnx and compared; the owner and the primary user choose from a blind
listening test. Samples are not committed (they are generated artifacts).

## Setup

- sherpa-onnx **2.0.3** (ONNX Runtime 1.28.2), CPU timings, median of several
  runs after warm-up. Text: the opening of *Dom Casmurro* (Machado de Assis,
  public domain) plus "— Capítulo 12. O ano era 1899…".
- Real-time factor (RTF) = synthesis time ÷ audio duration (lower is faster).

## Results

| Voice | Engine | Gender | Download | License | RTF 1 thread | RTF 4 threads | Automated transcription |
|---|---|---|---|---|---|---|---|
| pf_dora | Kokoro v1.0 int8 | F | 132 MB (all Kokoro voices) | Apache-2.0 | 1.12 | 0.70 | no errors |
| pm_alex | Kokoro v1.0 int8 | M | (same package) | Apache-2.0 | 1.00 | 0.68 | no errors |
| pm_santa | Kokoro v1.0 int8 | M | (same package) | Apache-2.0 | 1.01 | 0.74 | no errors |
| cadu-medium | Piper | M | 67 MB | data CC0, base voice research-only | 0.085 | 0.033 | no errors in 4/4 |
| faber-medium | Piper | M | 67 MB | same as cadu | 0.075 | 0.030 | no errors (best of 4) |
| jeff-medium | Piper | M | 67 MB | same as cadu | 0.093 | 0.037 | recurring errors |
| dii-high | Piper | F | 67 MB | non-commercial (CC BY-NC-ND) | 0.079 | 0.031 | minor recurring error |
| miro-high | Piper | M | 67 MB | non-commercial (CC BY-NC-ND) | 0.078 | 0.030 | quiet, background noise |
| edresson-low | Piper (16 kHz) | M | 67 MB | CC BY 4.0, base non-commercial | 0.064 | 0.024 | lower quality |

Dii and Miro: the README in their sherpa-onnx packages gives CC BY-NC-SA 4.0,
while the author's current model cards give CC BY-NC-ND 4.0 (both checked on
2026-11-27); the app names both.

Kokoro fp32 (350 MB) reached RTF 0.63 (1 thread) / 0.22 (4 threads); on this
CPU int8 was slower than fp32 for both engines. The phone trade-off must be
measured on the device.

## Implications for the app

- **Piper** voices are comfortably faster than real time: they can render
  just ahead of playback like the system voice.
- **Kokoro** is about real time on the PC; on a 2025 flagship it is estimated
  at 0.7–2.2× real time (worse under load), so chapters must be **rendered
  ahead** (for example while charging) — the cache and render-ahead design of
  ADR 0003 already supports this.
- **Phonemizer licence:** since 2.0.0, sherpa-onnx no longer bundles
  espeak-ng; Portuguese needs `piper-phonemize` (espeak-ng data), which is
  **GPL-3.0**. Shipping it makes the distributed app subject to GPL-3.0
  (source must stay public — it already is). Alternatives: sherpa-onnx 1.13.x
  (also GPL via espeak-ng) or a custom pronunciation lexicon (large effort).
  Needs an owner decision and an ADR before Phase 5 starts.
- Kokoro must be given the language `pt-br` explicitly (its default is
  English), and a leading dialogue dash should be spoken as a pause (it can
  produce a spurious "É" otherwise). Its output has a small DC offset that a
  high-pass filter removes.
- Licences: only Kokoro is free for any use; the Piper pt-BR voices are
  non-commercial or research-based, acceptable for this personal,
  non-commercial project but recorded per voice in the app's notices.

## Kokoro int8 tones (found on the phone, 2026-11-27)

The owner heard a thin, high metallic whistle behind Dora and Alex on
headphones. Spectra of the audio the app rendered on the phone show two narrow
tones at **4.8 kHz (+21 dB)** and **9.6 kHz (+24 dB)** above their
surroundings, also in the pauses; Piper and the phone's voice have none. They
sit at multiples of a fifth of Kokoro's 24 kHz rate (its inverse STFT steps 5
samples) and come from the **int8** weights: the fp32 model's render of the
same text has them at +4.5 and +5.5 dB, but its package is 350 MB instead of
132 MB. The app removes them with narrow notch filters (Q 60, two passes per
tone), which take both below the background while the rest of the spectrum
changes by 0.005 dB on average (0.09 dB at most), measured on the phone's
audio. The bundled Kokoro samples were made again with the same filters.

## Render speed on a modest phone (2026-11-27)

The owner reported that Dora and Alex stall every three or four sentences at
1.25×, while the phone's voice does not. `NeuralSpeedBenchmark` measured, on a
2023 mid-range test phone (Snapdragon 695: two fast and six efficient cores,
Android 15), a 20-second passage. "Listen ratio" is the render time divided by
the time the passage takes to hear at 1.25×; above 1.0 the player runs out of
audio.

| Model | Threads | Speed in the model | RTF | Listen ratio at 1.25× |
|---|---|---|---|---|
| Kokoro int8 (shipped) | 2 / 4 / 6 / 8 | 1.0 (player stretches) | 1.40 / 1.45 / 1.41 / 1.69 | 1.75 / 1.82 / 1.77 / 2.11 |
| Kokoro int8 (shipped) | 2 / 4 / 6 / 8 | 1.25 | 1.41 / 1.46 / 1.42 / 1.73 | 1.41 / 1.46 / 1.42 / 1.73 |
| Kokoro fp32 (350 MB) | 2 / 4 / 6 | 1.0 | 1.16 / 1.28 / 1.06 | 1.45 / 1.60 / 1.32 |
| Kokoro fp32 (350 MB) | 2 / 4 / 6 | 1.25 | 1.17 / 1.33 / 1.08 | 1.17 / 1.33 / 1.08 |
| Piper (Cadu) | 2 / 4 / 6 / 8 | 1.0 | 0.14 / 0.15 / 0.14 / 0.15 | 0.18 / 0.19 / 0.17 / 0.18 |
| Piper (Cadu) | 2 / 4 / 6 / 8 | 1.25 | 0.15 / 0.16 / 0.14 / 0.15 | 0.15 / 0.16 / 0.14 / 0.15 |

- Kokoro is slower than real time on such a phone even at 1.0×, so live
  listening stalls whatever the settings; only rendering ahead avoids it.
- More threads do not help Kokoro (eight is worse: the efficient cores slow
  the work down); the current cap of four is kept.
- Generating at the listening speed in the model instead of stretching in the
  player saves about 20 % of the render time for both families.
- The fp32 model is only about 20 % faster here (3× on the development PC), not
  enough to justify a 350 MB download.
- Piper renders about seven times faster than real time: it never stalls.

## Decisions (owner, 2026-11-27)

- Offer all nine voices in the Voices tab, each with its own small emblem and
  an on-screen sample; licence terms go to the in-app licences screen, not the
  voice cards.
- Accept GPL-3.0 for the APK only (repository code stays MIT). See
  [ADR 0018](../decisions/0018-offline-neural-voices.md).
