# Testing

## Strategy

| Level | Where | What it covers | Command |
|-------|-------|----------------|---------|
| Pure unit tests | `textkit/src/test` | Parsing, cleanup, segmentation, normalization, estimates | `./gradlew :textkit:test` |
| App unit tests | `app/src/test` | ViewModels, repositories, mapping, player logic with fakes | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests | `app/src/androidTest` | Room, import from real URIs, `SystemTtsEngine`, playback service through `MediaController` | `./gradlew :app:connectedDebugAndroidTest` |
| Compose UI tests | `app/src/androidTest` | Screens, navigation, accessibility checks | same as above |
| Static analysis | all modules | Android Lint, detekt, Spotless/ktlint | `./gradlew check` |
| Security tests | `textkit` + `app` | Malicious EPUB/PDF inputs, secret guard | see below |
| Manual device checklist | real phone | End-to-end behaviour that emulators fake poorly | see below |

Frameworks: `:textkit` uses JUnit Jupiter 6 with Truth; `:app` uses JUnit 4
(the Android default) with Truth, AndroidX Test and the Compose test APIs.

The facade player (`ReaderPlayer`) is tested under Robolectric with a real
internal ExoPlayer on Media3's `FakeClock` (`media3-test-utils-robolectric`,
`TestPlayerRunHelper`) and a fake speech engine that writes small silent WAV
files, so rendering, playlists, seeks, focus changes and the end of the book
run for real in milliseconds. Audio focus changes are delivered through
Robolectric's `ShadowAudioManager`. Player screens also run under
Robolectric with `createComposeRule` (on a phone-sized screen) to check
spoken labels, touch targets of at least 56 dp and the actions they send.

`./gradlew check` is the local gate and runs unit tests, lint, detekt and
formatting checks. CI runs the same plus the secret scan, CodeQL and the
documentation link check.

## Fixtures

- Generate EPUB and PDF fixtures **in test code** (small builders) instead of
  committing binary files.
- Use only synthetic text or public-domain text. Never commit copyrighted
  books or personal files.
- Keep fixtures minimal: one behaviour per fixture.

## Security tests

- **Secret guard:** `scripts/test-secret-guard.sh` must pass (also in CI).
- **Malicious EPUBs** (built in tests): path traversal entry names
  (`../../x`), zip bomb (huge declared/actual sizes, too many entries),
  XML with external entities or DTDs, missing/garbled container or OPF.
  Expected: a typed failure and a friendly message, never a crash, never a
  file written outside the book directory.
- **Corrupted and huge PDFs** (built in test code with PdfBox-Android,
  `PdfSecurityTest`): truncated, garbage and password-protected files,
  documents over the page, time, glyph and buffer limits, a looping outline
  and a page that cannot be decoded. Expected: a typed failure or bounded
  processing time and memory.

## Stress tests

- Import a 1,000-page PDF and an EPUB with 2 million characters; record time
  and peak memory.
- Hammer ±15 s and chapter jumps rapidly; the position must stay consistent.
- Play for 2 hours with the screen locked; check battery drain and memory.

## Test devices

- **Reference:** Android 17 (API 37) — the primary user's phone and the
  `rreader-api37` emulator (Pixel profile, Google Play image, so Google's TTS
  voices are available). See [ADR 0014](decisions/0014-target-android-17.md).
- **Compatibility spot checks:** Android 16 (`rreader-api36` emulator) and the
  minimum supported version when touching platform APIs.
- The emulator needs hardware virtualization (KVM). On AMD machines enable
  "SVM Mode" in the firmware settings if `/dev/kvm` is missing.
- During playback tests, enable Android 17's strict background-audio check:
  `adb shell cmd audio set-enable-hardening throw` (restore with `disable`).

## Manual device checklist

Run on a real phone (ideally the primary user's model) before installing a
new version:

1. Share a PDF and an EPUB from a messaging app to R Reader.
2. Import completes; the book appears with cover and title; tap play.
3. Lock the screen for 30 minutes; playback continues.
4. Use play/pause and next/previous on a Bluetooth headset.
5. Receive a phone call: playback pauses, then resumes after the call.
6. Force-close the app and reopen it: playback resumes at the same sentence.
7. Turn on airplane mode: playback continues.
8. Increase the system font size to the maximum: screens stay usable.
9. Turn on TalkBack: every control is announced.
