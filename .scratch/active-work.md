# Active work — per-machine claims

Single coordination point across every work collection (gameplay-
pipeline slices, physics-audit S-list, refactor-backlog items, polish
bag, ad-hoc tasks). One row per active claim; remove when the work
lands. WIP=1 per workstream still applies — see
[`.claude/rules/multi-machine-workflow.md`](../.claude/rules/multi-machine-workflow.md).

Per-collection status markers (⏳ in `settings-pipeline-slices.md`,
✅/⏳/🔲 inline status in `physics-audit.md`'s S-list, etc.) stay
where they live. This file is the **claimed by whom** layer; the
source files are the **what is the work** layer.

## Active claims

- **laptop** — Slice S1: drag → mphys native linear damping
  ([physics-audit.md](./physics-audit.md)); branch
  `slice/s1-damping`; status: implementing — math fit, drop drag gate,
  PlayerDriver-side setDamping; angularDamping=1.0 on player ships

## Recently landed (last 7 days)

_Empty — populate as work lands; trim entries older than ~7 days._

## Conventions

- **Row shape:** `**{machine}** — {work title} ({source-file}); branch
  \`{branch-name}\`; status: {one-line note}`
- **Branch naming:**
  - `slice/<short-name>` for queue slices and audit S-slices
  - `chore/<short-name>` for tracker hygiene / cleanup
  - `fix/<issue-or-symptom>` for bug fixes
  - `refactor/<short-name>` for refactors
- **Stop conditions:** never start work without a row; never delete
  another machine's row.
