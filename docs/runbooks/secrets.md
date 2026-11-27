# Secrets: where they go and what to do if one leaks

The rules are in [ADR 0013](../decisions/0013-layered-secret-protection.md) and
[security.md](../security.md). Today the app needs **no** secrets.

## Setting up the guard (every clone)

```bash
git config core.hooksPath .githooks       # also done by the Gradle build
GOBIN=$HOME/.local/bin go install github.com/zricethezav/gitleaks/v8@latest
scripts/test-secret-guard.sh             # must print "all checks passed"
```

Never commit with `--no-verify`. If the hook blocks a false positive, fix the
content (or, as a last resort, add a narrowly scoped allowlist entry to
`.gitleaks.toml` with a justification in [security.md](../security.md)).

## Adding an API key (if one is ever needed)

1. Add the key name with a placeholder value to `secrets.defaults.properties`
   (committed), for example `EXAMPLE_API_KEY=` with an empty value.
2. Put the real value in `secrets.properties` at the repository root
   (git-ignored; never commit it).
3. The Gradle build exposes every key listed in `secrets.defaults.properties`
   as a `BuildConfig` string field, taking the value from
   `secrets.properties` when present.
4. In CI, store the value as a GitHub Actions secret and write it to
   `secrets.properties` in the workflow step that needs it.
5. Remember: anything compiled into the APK can be extracted. Only use keys
   that are safe to ship (restricted, rate-limited, revocable). A truly
   sensitive key needs a server-side proxy, which requires a new ADR.

## Values kept out of the repository that are not secrets

`CATALOG_CONTACT` is the contact (a web page or an e-mail address) that the
catalogue adds to its User-Agent, as Project Gutenberg asks of apps that use
its catalogue ([ADR 0017](../decisions/0017-free-catalogue-from-project-gutenberg.md)).
It is not secret, but it identifies the maintainer, so it lives in
`secrets.properties` like a key (for example
`CATALOG_CONTACT=https://example.org/r-reader`) and is empty in the
repository. Set it before building a release.

## If a secret leaks (it is compromised from the moment it was committed)

Do these in order and do not stop early:

1. **Rotate/revoke it at the provider** — this is the actual fix.
2. Replace the value in code with the `secrets.properties` mechanism; commit.
3. Purge it from git history (`git filter-repo`), force-push `main`, and ask
   anyone with a clone to re-clone.
4. Check the provider's logs for use during the exposure window.
5. Find out why the guard missed it and strengthen the hook or
   `.gitleaks.toml`.

## Release signing key

The signing keystore and its passwords never enter the repository. See
[release-signing.md](release-signing.md).
