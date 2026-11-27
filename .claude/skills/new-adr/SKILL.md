---
name: new-adr
description: Record an architecture decision for R Reader. Use whenever a non-trivial technical or product decision is made or an existing decision changes.
---

# Record a decision (ADR)

1. Find the next number: the highest `NNNN-*.md` in `docs/decisions/` plus one.
2. Copy `docs/decisions/0000-template.md` to `docs/decisions/NNNN-short-title.md`
   and fill in context, decision, alternatives and consequences. Be concrete
   enough that code can be checked against it.
3. Add a row to the index in `docs/decisions/README.md`.
4. If it replaces an earlier decision, set the old ADR's status to
   "Superseded by" plus a link to the new ADR — never rewrite its content.
5. Update `docs/architecture.md`, `docs/domain.md` or `AGENTS.md` if the
   decision changes them, and commit everything with the code it affects.
