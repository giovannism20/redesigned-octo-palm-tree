# Security and privacy

Threat model and security requirements for R Reader. Update it when the
architecture changes (new input, new network call, new exported component,
new dependency class). Reporting policy: [SECURITY.md](../SECURITY.md).

## System overview

- **Architecture:** single Android app, no backend, no accounts.
- **Stack:** Kotlin, Jetpack Compose, Media3, Room, WorkManager, PdfBox-Android,
  ML Kit text recognition (on-device), Android TTS; later sherpa-onnx.
- **Data handled:** books the user imports (may be copyrighted or personal),
  reading positions and settings. No credentials, no contact data, no
  location, no identifiers are collected.
- **Network:** only on explicit user action — public-domain book downloads and
  voice model downloads, over HTTPS.
- **Distribution:** release APK signed by the maintainer, installed via ADB.

## Assets

| Asset | Sensitivity | Location |
|-------|-------------|----------|
| Imported books and extracted text | Private to the user | App-private storage |
| Reading positions, settings, per-book voice | Low (reveals reading habits) | Room / DataStore |
| App integrity (code that runs on the phone) | High | Signed APK |
| Release signing key | Critical (controls updates) | Maintainer machine, outside the repo, backed up |
| Repository and CI integrity | High | GitHub |

## Trust boundaries

| # | Boundary | From → To | Controls |
|---|----------|-----------|----------|
| B1 | File intake | Any file chosen/shared by the user → importer | Signature-based type detection, size limits, parsing limits, typed failures |
| B2 | Inter-app intents and media controllers | Other apps → exported activity / media session service | Minimal exported components, accept only `content://` URIs with read grants, validate MIME and content; the media session accepts only trusted controllers and book-id requests |
| B3 | Network downloads | Internet → catalogue/model downloader | HTTPS only (no cleartext), host allowlist, size limits, SHA-256 pinning for models |
| B4 | TTS engine | App → third-party TTS engine process | Prefer offline voices; flag voices that need the network (their text leaves the device) |
| B5 | Build supply chain | Dependencies, Gradle wrapper, Actions → build | Version catalog, Dependabot, wrapper validation, Actions pinned by SHA, CodeQL |
| B6 | Public repository | Developer machines → GitHub | Secret guard layers, no personal data, reviewed third-party content |

## STRIDE analysis

| Threat | Component | Risk | Scenario | Mitigation |
|--------|-----------|------|----------|------------|
| Tampering / EoP | EPUB parser (B1) | High | ZIP entry `../../databases/x` overwrites app files (zip slip) | Never write files using archive entry names; read entries in memory by exact name |
| DoS | EPUB parser (B1) | Medium | Zip bomb or millions of entries exhaust memory/storage | Caps on entry count, per-entry and total uncompressed size; streaming reads |
| Info disclosure / DoS | XML parsing (B1) | Medium | External entities or DTD expansion (XXE, billion laughs) | DTD and external entities disabled on every XML parser |
| DoS | PDF parsing (B1) | Medium | Malformed or gigantic PDF hangs or crashes the app | Parse in a worker with page, time, glyph and buffer limits (SR-4); password-protected files refused; out-of-memory reported as a typed failure so WorkManager does not retry a crash forever |
| DoS | Cover rendering (B1) | Low | A crafted first page crashes or stalls the platform PDF renderer (native code) | Rendered only after PdfBox read the file, one page downscaled to at most 600 x 900 px, inside the import worker; a failure means no cover |
| Tampering | Intents (B2) | Medium | Another app sends a crafted `file://` URI or huge stream | Accept `content://` only, copy with size limit, re-detect type |
| Spoofing / Tampering | Downloads (B3) | Medium | Network attacker swaps a voice model or book | HTTPS only; pinned SHA-256 for models; validate books like any import |
| Info disclosure | TTS engine (B4) | Low/Medium | Network-backed voices send book text to a vendor | Default to offline voices; label network voices in the UI |
| Info disclosure / Tampering | Media session (B2) | Low | Another app connects to the exported playback service to see what is being listened to, or to control playback | Untrusted controllers are rejected; media ids must be book UUIDs (optionally with a chapter); the notification's content intent is explicit and immutable; no media button receiver, so playback never starts from the background |
| Info disclosure | Logs | Low | Book text or file names in logs | No content/file names in release logs |
| Info disclosure | Backups | Low | Cloud backup copies reading history | Cloud backup disabled; device-to-device transfer allowed |
| Tampering | Supply chain (B5) | Medium | Compromised dependency, wrapper or action | Wrapper validation, pinned Actions, Dependabot, CodeQL, minimal permissions |
| Info disclosure | Repository (B6) | High | A key or keystore is committed | Pre-commit hook, CI scan, GitHub push protection, `.gitignore`, review |
| EoP | Update channel | Medium | A malicious APK replaces the app | Release signing key never leaves the maintainer's control; installs only from the maintainer |

## Security requirements

Each requirement is testable; reference its ID in tests and reviews.

- **SR-1** Archive entry names are never used to build file-system paths
  (verified by `EpubSecurityTest`).
- **SR-2** EPUB reading enforces limits: entry count, per-entry and total
  uncompressed size; violations yield a typed `ImportError` (verified by
  `EpubSecurityTest`).
- **SR-3** Every XML parser disables DTDs and external entities (verified by
  `EpubSecurityTest`).
- **SR-4** PDF extraction and OCR run with page, time and memory limits.
  (`PdfLayoutExtractor` with `PdfLimits`: documents over 3,000 pages are
  refused before any text is read; extraction stops after 5 minutes, checked
  before every page and every 1,024 glyphs, and as soon as its import is
  cancelled; at most 20 million glyphs are read per document and 100,000 per
  page; PdfBox buffers at most 16 MiB in memory and 512 MiB in all, the rest
  in a temporary file in the app cache; the outline reader visits at most
  5,000 entries, 32 levels deep, and stops at loops; running out of memory or
  stack ends in a typed failure. Verified by `PdfSecurityTest`.)
- **SR-5** Cleartext traffic is disabled (`network_security_config`); only
  allowlisted HTTPS hosts are contacted, and only after a user action.
- **SR-6** Downloaded voice models are verified against SHA-256 values pinned
  in code before use.
- **SR-7** Exported components are limited to the launcher/intake activity and
  the media session service; intake accepts only `content://` URIs. (`BookIntake`; content is re-detected by signature and copied with a
  300 MB limit. `PlaybackService` must be exported for the system to bind
  it; `ReaderSessionCallback` accepts only trusted controllers — this app,
  the system and apps the user allowed to control media — and only book ids.
  Media3 also merges its `BluetoothValidationActivity`, exported but guarded
  by the `BLUETOOTH_PRIVILEGED` permission that only the system Bluetooth app
  holds.)
- **SR-8** Release builds log no book content, file names or personal data. (`Diagnostics` logs only
  exception types.)
- **SR-9** Cloud backup is disabled; device-to-device transfer is allowed.
- **SR-10** No secrets in the repository (see the layers below).
- **SR-11** Dependencies are pinned in the version catalog and monitored by
  Dependabot; CI runs CodeQL and validates the Gradle wrapper.
- **SR-12** No `WebView`, dynamic code loading or reflection-based plugins.

## Permissions

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Listening with the screen locked runs in a `mediaPlayback` foreground service ([ADR 0014](decisions/0014-target-android-17.md)). |
| `WAKE_LOCK` | The player keeps the CPU awake while reading with the screen off. |
| `ACCESS_NETWORK_STATE` | Merged from ExoPlayer; unused, and the app has no `INTERNET` permission yet. |
| `RECEIVE_BOOT_COMPLETED` | Merged from WorkManager, which reschedules pending import jobs after a restart. |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature permission defined by AndroidX Core to keep runtime receivers private. |

No runtime permission is requested: the playback notification is a media
notification, exempt from the notification permission. Other exported
components merged from libraries require system-only permissions
(`BIND_JOB_SERVICE`, `DUMP`), and the tooling activities exist only in debug
builds.

## Secret protection layers

1. `.gitignore` covers secrets, keystores, env files and release binaries.
2. `secrets.properties` (git-ignored) is the only place for keys, read by
   Gradle; `secrets.defaults.properties` holds placeholders. Keys compiled
   into an APK are extractable, so a truly sensitive key would require a
   server, which is out of scope ([ADR 0013](decisions/0013-layered-secret-protection.md)).
3. `.githooks/pre-commit` blocks secret-bearing files and runs gitleaks on the
   staged diff. Never bypass it.
4. CI scans the full history with gitleaks on every push.
5. GitHub secret scanning and push protection are enabled.
6. The release keystore and its passwords live outside the repository.

Leak response: [runbooks/secrets.md](runbooks/secrets.md).

## Privacy

- Data stays on the device. There is no account, server, analytics or ads.
- Network calls: catalogue browsing/downloads and voice model downloads, each
  started by the user.
- The system TTS engine is a separate app chosen by the user; offline voices
  are preferred so book text does not leave the phone.

## Review checklist (end of each phase)

- [ ] New inputs or network calls are reflected in this document.
- [ ] SR-1 … SR-12 still hold; new tests cover new parsing paths.
- [ ] Exported components and permissions unchanged or justified.
- [ ] No secrets, personal data or third-party branding in the diff.
- [ ] Dependency changes reviewed (Dependabot PRs or manual bumps).
