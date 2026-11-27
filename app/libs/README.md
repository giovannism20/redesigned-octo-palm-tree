# Vendored libraries

The offline neural voices ([ADR 0018](../../docs/decisions/0018-offline-neural-voices.md))
need two Android libraries that are published only as files on their GitHub
releases, not in Maven Central or Google's Maven repository (a third-party
repackaging there is not used). They are **not kept in git** (about 53 MB):
the build downloads each file, exactly as released, from its release into
this folder when it is missing (`fetchVendoredLibraries`, before every build;
the files are git-ignored), and refuses any file whose SHA-256 differs from
[`SHA256SUMS`](SHA256SUMS). The values below are the ones GitHub publishes for
the release assets and were checked against fresh downloads on 2026-11-27.
The first build therefore needs access to github.com.

| File | Release | License | SHA-256 |
|------|---------|---------|---------|
| `sherpa-onnx-static-link-onnxruntime-2.0.3.aar` | [k2-fsa/sherpa-onnx v2.0.3](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.3) | Apache-2.0; ONNX Runtime, linked statically, MIT | `7744a3a8f08f017d59c2d8ed8d9b700345561f5abe9f4e06dc6eaef3a0b977b4` |
| `piper-phonemize-1.6.1.aar` | [csukuangfj/piper-phonemize v1.6.1](https://github.com/csukuangfj/piper-phonemize/releases/tag/v1.6.1) | GPL-3.0-or-later (it contains espeak-ng and its data) | `e8cf1b31c85a199f3258144bc5c4c23b1ce942c3e5ed7fea7dd0d4227980e285` |

- **sherpa-onnx** runs the voice models (Kokoro and Piper) on the phone. The
  variant with ONNX Runtime linked statically is used: one native library per
  CPU architecture (about 25 MB for `arm64-v8a`) instead of three, and the
  file stays below GitHub's 50 MB warning size. Its Kotlin classes are called
  from native code by name, so `proguard-rules.pro` keeps them.
- **piper-phonemize** turns Portuguese text into phonemes with espeak-ng;
  sherpa-onnx 2.x no longer does this itself. The library is GPL-3.0, so the
  APK that includes it is distributed under GPL-3.0 (ADR 0018), and the
  in-app "About and licences" screen shows the license and links to this
  repository. Its own copy of the espeak-ng data, for every language
  (19 MB), is left out of the APK (`ignoreAssetsPattern` in
  `app/build.gradle.kts`); the app ships only the Portuguese part in
  `app/src/main/assets/espeak-pt/`, unmodified files copied from this AAR,
  plus the English, German, French and Polish dictionaries (about 380 KB):
  espeak-ng switches to them from Portuguese (English to name letters it
  cannot name in Portuguese, German and French for a few words, Polish for a
  German one), and without them its native code crashed the app on ordinary
  book text (`EspeakPhonemizerTest`). `EspeakDictionariesTest` fails when a
  shipped dictionary switches to one that is not shipped.

Corresponding source code: sherpa-onnx at tag
[`v2.0.3`](https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.3),
piper-phonemize at tag
[`v1.6.1`](https://github.com/csukuangfj/piper-phonemize/tree/v1.6.1) (it
builds espeak-ng from [rhasspy/espeak-ng](https://github.com/rhasspy/espeak-ng)).

## Updating

1. Download the new release files and compare their SHA-256 with the digest
   GitHub shows for each asset.
2. Update `SHA256SUMS` (`sha256sum *.aar > SHA256SUMS`), the table above, and
   the names and release URLs in `vendoredLibraryUrls` and the dependencies in
   `app/build.gradle.kts`. Delete the old files here; the build downloads the
   new ones.
3. If piper-phonemize changed, copy the espeak-ng files listed in
   `EspeakData.FILES` again (see `app/src/main/assets/espeak-pt/`), change
   `EspeakData.VERSION` so phones copy them again, and check that the phonemes
   of a test sentence did not change.
4. Run the full gate and the instrumented `SherpaOnnxEngineTest` and
   `EspeakPhonemizerTest` on a phone.
