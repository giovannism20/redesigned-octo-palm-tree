# 0011. AI-first documentation

- **Status:** Accepted
- **Date:** 2026-11-26

## Context

Most changes are made by AI agents in sessions that do not share memory.
Decisions taken in chat are lost unless they are written down where the next
agent looks first.

## Decision

- `AGENTS.md` is the single entry point (rules, commands, repository map,
  reading order); `CLAUDE.md` only imports it.
- Decisions live in `docs/decisions/` as ADRs; architecture, domain glossary,
  roadmap, testing and security live in `docs/`; operational steps live in
  `docs/runbooks/`.
- Docs change in the same commit as the code they describe; CI checks links.
- `docs/roadmap.md` is the resume point for any session.
- Specialist agent definitions are vendored in `.claude/agents/` so every
  session has them.

## Alternatives considered

- **Chat history and personal notes** — not shared across sessions or tools.
- **A wiki** — drifts away from the code it documents.

## Consequences

- Positive: any agent or person can pick up work without the original
  conversation; decisions are auditable.
- Negative: documentation is part of the definition of done and costs time.
