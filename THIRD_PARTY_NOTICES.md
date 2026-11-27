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
| Material Icons (vector paths in `app/src/main/res/drawable/ic_*.xml`) | Apache License 2.0 |
| jsoup (EPUB XML/XHTML parsing in `:textkit`) | MIT License (full text below) |

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
