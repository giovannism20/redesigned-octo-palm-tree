# 0010. Own brand identity and app identifiers

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

The repository is public. Referencing or imitating other consumer apps
(names, logos, screenshots, copy or distinctive layouts) could lead to
trademark or takedown problems. The application id is permanent once the app
is installed: changing it means uninstalling and losing local data.

## Decision

- The app is called **R Reader**, with its own logo, palette and icons.
- The repository never references, copies or imitates other consumer apps or
  their branding, and never contains reference screenshots. Naming the
  technologies and services the project uses or evaluated is fine.
- Common interaction patterns (library list, mini-player, bottom navigation)
  are used as generic patterns, not copies.
- `applicationId` is `com.giovanni_martins.rreader`.
- The Kotlin namespace and package root is `com.giovannimartins.rreader`,
  because Kotlin conventions, ktlint and detekt reject underscores in package
  names. Android allows the namespace and application id to differ.

## Alternatives considered

- **GitHub-based identifiers** (`io.github.<user>`) — rejected by the
  maintainer.
- **Underscore in the Kotlin package** — would require disabling naming rules.

## Consequences

- Positive: no brand risk; stable identifiers.
- Negative: the namespace and application id differ, which must be remembered
  when reading manifests and logs.
