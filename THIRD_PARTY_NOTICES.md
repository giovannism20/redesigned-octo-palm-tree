# Third-party notices

## Agent definitions in `.claude/agents/`

The following files are copied unmodified from
[msitarzewski/agency-agents](https://github.com/msitarzewski/agency-agents)
(commit `b859c51`) and are used under the MIT License below:

- `design-brand-guardian.md`
- `design-ui-designer.md`
- `design-ux-researcher.md`
- `design-ui-finish-gate-reviewer.md`
- `security-secrets-credential-engineer.md`
- `security-architect.md`
- `security-appsec-engineer.md`
- `security-ai-generated-code-auditor.md`
- `engineering-mobile-app-builder.md`
- `engineering-code-reviewer.md`

Project rules in [AGENTS.md](AGENTS.md) take precedence over these
definitions.

```text
MIT License

Copyright (c) 2025 AgentLand Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Application dependencies

Libraries used by the app are declared in `gradle/libs.versions.toml`.

| Component | License |
|-----------|---------|
| AndroidX (Compose, Activity, Lifecycle, Room, WorkManager, Media3, DataStore, Hilt integrations; AppCompat, which comes with ML Kit) | Apache License 2.0 |
| Kotlin standard library, kotlinx.coroutines and kotlinx.serialization (OCR pages saved as JSON) | Apache License 2.0 |
| Dagger / Hilt | Apache License 2.0 |
| Coil | Apache License 2.0 |
| Material Icons (vector paths in `app/src/main/res/drawable/ic_*.xml`: library, add book, voices, play, pause, speed, chapters, minimize, notification headset, sleep timer, text, back, explore, search, download, check, expand more, expand less, stop) | Apache License 2.0 |
| AndroidX Media3 "skip back/forward 15" icons (vector paths in `ic_replay_15.xml` and `ic_forward_15.xml`, Copyright 2024 The Android Open Source Project) | Apache License 2.0 |
| jsoup (EPUB XML/XHTML parsing in `:textkit`) | MIT License (full text below) |
| PdfBox-Android (PDF text extraction) | Apache License 2.0 (NOTICE below) |
| Bouncy Castle (cryptography used by PdfBox-Android) | MIT License (full text below) |
| ML Kit Text Recognition v2 with the bundled Latin model (`com.google.mlkit:text-recognition` and the ML Kit `common`, `vision-common`, `vision-interfaces`, `text-recognition-bundled-common` and `play-services-mlkit-text-recognition` libraries it brings) | ML Kit Terms of Service (see below) |
| Google Play services Base, Basement and Tasks, and ODML Image, which come with ML Kit | Android Software Development Kit License |
| Firebase components and encoders, and Google datatransport, which come with ML Kit | Apache License 2.0 |
| sherpa-onnx (on-device neural speech, AAR downloaded by the build, see `app/libs/README.md`) | Apache License 2.0 |
| ONNX Runtime (linked statically into sherpa-onnx's native library) | MIT License (full text below) |
| piper-phonemize with espeak-ng and its Portuguese data plus the English, German, French and Polish dictionaries (pronunciation for the neural voices, AAR downloaded by the build, data in `app/src/main/assets/espeak-pt/`) | GNU General Public License v3.0 or later (see below) |
| Apache Commons Compress, with Commons IO, Lang and Codec (unpacking downloaded voice packages) | Apache License 2.0 (NOTICE below) |

### jsoup

[jsoup](https://jsoup.org/) parses the XML and XHTML inside EPUB files in
`:textkit`. Used under the MIT License:

```text
The MIT License

Copyright (c) 2009-2026 Jonathan Hedley <https://jsoup.org/>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### PdfBox-Android

[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), a port of
[Apache PDFBox](https://pdfbox.apache.org/) to Android, reads the text layer,
outline and document information of PDF books. Used under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). As that
license requires, its NOTICE is reproduced here:

```text
Apache PDFBox
Copyright 2014 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).

Based on source code originally developed in the PDFBox and
FontBox projects.

Copyright (c) 2002-2007, www.pdfbox.org

Based on source code originally developed in the PaDaF project.
Copyright (c) 2010 Atos Worldline SAS

Includes the Adobe Glyph List
Copyright 1997, 1998, 2002, 2007, 2010 Adobe Systems Incorporated.

Includes the Zapf Dingbats Glyph List
Copyright 2002, 2010 Adobe Systems Incorporated.

Includes the Script Property (Scripts-10.0.0.txt)
Copyright 2017 Unicode, Inc.
```

The library also bundles, unmodified and with their own notices inside it,
the Adobe Font Metrics of the 14 standard PDF fonts (Adobe Systems, free to
use and redistribute with their notices) and the Liberation Sans font
(Red Hat, SIL Open Font License 1.1), which PdfBox uses as a fallback font.

### ML Kit text recognition

[ML Kit](https://developers.google.com/ml-kit) Text Recognition v2 reads the
pages of scanned PDFs on the phone ([ADR 0015](docs/decisions/0015-on-device-ocr-with-bundled-ml-kit.md)).
It is not open source: Google offers it free of charge under the
[ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) and the
[Google APIs Terms of Service](https://developers.google.com/terms), and the
Google Play services libraries that come with it under the
[Android Software Development Kit License](https://developer.android.com/studio/terms).
R Reader bundles the Latin recognition model in the app, so text recognition
never downloads anything and works offline. The app has no network
permission and unregisters the backend that would upload ML Kit's usage
logs, so nothing about the books or the app leaves the phone
([ADR 0012](docs/decisions/0012-no-accounts-local-only-data.md)).

ML Kit's native library is built from open-source components (among them
TensorFlow Lite, Abseil, Protocol Buffers, Eigen, ICU and zlib). Their
license texts are shipped by Google with the libraries, in the
`third_party_licenses.txt` file of each ML Kit and Google Play services
package.

### Atkinson Hyperlegible Next

The app's typeface, [Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next)
(Braille Institute, Applied Design Works, Elliott Scott, Megan Eiswerth,
Letters From Sweden), is bundled unmodified as
`app/src/main/res/font/atkinson_hyperlegible_next.ttf` (from the Google Fonts
repository, SHA-256 `5a455d1c…5e31745`). Copyright 2020-2024 The Atkinson
Hyperlegible Next Project Authors. It is licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org); the full licence text
ships with the app in
`app/src/main/assets/licenses/OFL-1.1-atkinson-hyperlegible-next.txt`.

### Project Gutenberg

The Explore tab searches and downloads public-domain books from
[Project Gutenberg](https://www.gutenberg.org/) when the user asks, following
its terms for catalogue apps
([ADR 0017](docs/decisions/0017-free-catalogue-from-project-gutenberg.md)).
R Reader is not affiliated with Project Gutenberg. The curated list
(`app/src/main/assets/catalog/curated.json`) holds only ebook numbers, titles
and authors, with descriptions written for this project. A downloaded book is
stored as Gutenberg delivers it, license included; the license is not read
aloud. Gutenberg's books are in the public domain in the United States, and
Gutenberg asks readers elsewhere to check the laws of their country; the
authors of the suggested classics all died before 1925.

### Bouncy Castle

[Bouncy Castle](https://www.bouncycastle.org/) comes with PdfBox-Android,
which uses it for certificate-encrypted PDFs. Used under the MIT License:

```text
Copyright (c) 2000 - 2023 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Offline neural voices: sherpa-onnx, ONNX Runtime, piper-phonemize and espeak-ng

The neural voices ([ADR 0018](docs/decisions/0018-offline-neural-voices.md))
run on the phone with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
2.0.3 (Apache License 2.0), whose native library has
[ONNX Runtime](https://github.com/microsoft/onnxruntime) linked in (MIT
License, below). Text is turned into phonemes by
[piper-phonemize](https://github.com/csukuangfj/piper-phonemize) 1.6.1, which
contains [espeak-ng](https://github.com/espeak-ng/espeak-ng), built from a fork
at the commit given in [app/libs/README.md](app/libs/README.md), and its
pronunciation data. Both are licensed under the
[GNU General Public License v3.0 or later](https://www.gnu.org/licenses/gpl-3.0.html),
so the APK that includes them is distributed under the GPL-3.0; the source
code in this repository stays MIT. The app shows the full license texts in
its "About and licences" screen with a link to this repository. The release
files, their checksums and the tags and commits of their source code are
listed in [app/libs/README.md](app/libs/README.md); the espeak-ng files in
`app/src/main/assets/espeak-pt/` are copied unmodified from the
piper-phonemize AAR.

```text
MIT License

Copyright (c) Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Apache Commons

[Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)
unpacks the downloaded voice packages (bzip2-compressed tar archives); it
brings Commons IO, Commons Lang and Commons Codec. Used under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). Their
NOTICE files:

```text
Apache Commons Compress
Copyright 2002-2025 The Apache Software Foundation

Apache Commons IO
Copyright 2002-2025 The Apache Software Foundation

Apache Commons Lang
Copyright 2001-2025 The Apache Software Foundation

Apache Commons Codec
Copyright 2002-2025 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (https://www.apache.org/).
```

### Voice models (downloaded on demand)

The neural voices are not part of the APK: the listener downloads them from
the official [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models),
and the app checks each archive's SHA-256 before using it. Their terms, as
recorded in the [voice evaluation](docs/design/voices-evaluation.md) and shown
in the app's "About and licences" screen:

| Voices | Model | Terms |
|--------|-------|-------|
| Dora, Alex, Santa | Kokoro v1.0 (82M), int8 | Apache License 2.0 (the package's LICENSE; Kokoro-82M weights Apache-2.0) |
| Cadu, Faber, Jeff | Piper pt_BR (medium) | Recordings from Nabu Casa's voice datasets under CC0; fine-tuned from the en_US lessac voice, whose dataset (Blizzard 2013 / Lessac) is licensed for research only; piper-voices repository MIT |
| Dii, Miro | Piper pt_BR (high) | Voices of real people owned by TigreGotico, trained on a synthetic dataset; non-commercial only (CC BY-NC-SA 4.0 in the sherpa-onnx README, CC BY-NC-ND 4.0 on the author's current card) |
| Edresson | Piper pt_BR (low) | TTS-Portuguese Corpus by Edresson Casanova et al., CC BY 4.0 (attribution); fine-tuned from the en_US ryan voice, whose RyanSpeech dataset is CC BY-NC-SA 4.0 |

R Reader is a free, non-commercial personal project, which these terms allow.

### Voice samples

`app/src/main/assets/voices/samples/*.ogg` are the first sentence of the
opening of *Dom Casmurro* (Machado de Assis, public domain) spoken by each
voice above with sherpa-onnx 2.0.3 during the voice evaluation, filtered,
levelled and encoded as Ogg Opus by `scripts/make_voice_samples.py`. They are
output of the models, under the same terms as the models.

### Licence texts in the app

`app/src/main/assets/licenses/` holds the texts the "About and licences"
screen shows: the GNU GPL 3.0 and the Apache License 2.0 (from Debian's
`common-licenses`), the Apache 2.0 text with the PdfBox-Android and Apache
Commons notices, the MIT licences of R Reader, ONNX Runtime, jsoup and
Bouncy Castle (as above), and the font's SIL Open Font License.
