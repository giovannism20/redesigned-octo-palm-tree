# 0008. Commit directly to `main`

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

This is a personal project with one maintainer working with AI agents. Issue
and pull request ceremony adds overhead without a second reviewer.

## Decision

- Commit directly to `main` in small Conventional Commits.
- Quality is enforced by automation instead of ceremony: pre-commit secret
  guard, `./gradlew check` locally, CI on every push.
- A red `main` is fixed before any other work.
- Parallel work happens in separate worktrees and is rebased onto `main`
  locally.

## Alternatives considered

- **Pull requests with review** — useful with several contributors; can be
  reintroduced later without changing the codebase.

## Consequences

- Positive: fast iteration, linear history that documents intent.
- Negative: no pre-merge review gate; specialist review agents are used before
  larger changes and at the end of each phase instead.
