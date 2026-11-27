# Security policy

R Reader is a small, offline-first Android app maintained as a personal
open-source project. It has no accounts, no backend and no telemetry, so the
attack surface is the files a user imports and the optional downloads
(public-domain books and voice models). See [docs/security.md](docs/security.md)
for the threat model.

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub's
**"Report a vulnerability"** button on the repository's *Security* tab
(private vulnerability reporting). Do not open a public issue or discussion.

Include, when possible:

- the affected version or commit;
- a minimal file or steps that reproduce the problem;
- the impact you observed (crash, data exposure, code execution, etc.).

You can expect an acknowledgement within a week. Fixes are released as a new
app version and noted in [CHANGELOG.md](CHANGELOG.md).

## Secrets

No credentials belong in this repository. If you find one, report it privately
as above so it can be revoked and purged from history.
