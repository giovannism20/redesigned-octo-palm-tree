# 0002. Free, on-device voices only

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The app exists so a family member can listen to books without recurring
costs. A long book has around 0.5–2 million characters (roughly 54,000
characters per hour of speech). Cloud neural voices cost about US$ 16–30 per
million characters after small monthly free tiers, require an account, an API
key and internet access, and can bill unexpectedly. Premium voice APIs cost
tens of dollars per book. Android phones ship with a free offline TTS engine,
and open-source neural models (Piper, Kokoro) can run on-device through
sherpa-onnx.

## Decision

Use only free voices that run on the phone:

1. **Now:** the Android system TTS engine (Google or Samsung voices), preferring
   offline voices.
2. **Later:** downloadable open-source neural voices running in-app through
   sherpa-onnx (Piper and Kokoro Portuguese voices), chosen with the user from
   listening samples.

No paid or cloud text-to-speech service is integrated. Adding one requires a
new ADR and must keep a free path as the default.

## Alternatives considered

- **Cloud neural TTS with free tiers** (for example Azure AI Speech or Google
  Cloud TTS) — clearly more natural, but capped free tiers (roughly 9–18 hours
  of audio per month), accounts, keys, internet dependency and billing risk.
- **Self-hosted models on a home computer** — free per use and high quality,
  but requires a machine that is always on and reachable; operational burden.
- **Premium voice APIs** — best quality, prohibitive per-book cost.

## Consequences

- Positive: zero running cost, works offline, no keys to protect, private.
- Negative: system voices sound less natural than premium cloud voices;
  neural on-device voices may be slower than real time on budget phones.
- Follow-ups: the rendering pipeline is engine-agnostic
  ([ADR 0003](0003-sentence-audio-rendering-with-media3.md)) so better free
  voices can be added without touching playback.
