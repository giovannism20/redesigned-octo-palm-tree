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
| AndroidX (Compose, Activity, Lifecycle, Room, WorkManager, Media3, DataStore, Hilt integrations) | Apache License 2.0 |
| Kotlin standard library and kotlinx.coroutines | Apache License 2.0 |
| Dagger / Hilt | Apache License 2.0 |
| Coil | Apache License 2.0 |
| Material Icons (vector paths in `app/src/main/res/drawable/ic_*.xml`: library, add book, voices, play, pause, speed, chapters, minimize, notification headset) | Apache License 2.0 |
| AndroidX Media3 "skip back/forward 15" icons (vector paths in `ic_replay_15.xml` and `ic_forward_15.xml`, Copyright 2024 The Android Open Source Project) | Apache License 2.0 |
| jsoup (EPUB XML/XHTML parsing in `:textkit`) | MIT License (full text below) |
| PdfBox-Android (PDF text extraction) | Apache License 2.0 (NOTICE below) |
| Bouncy Castle (cryptography used by PdfBox-Android) | MIT License (full text below) |

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
