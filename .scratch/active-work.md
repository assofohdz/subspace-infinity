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

## Recently landed (last 7 days)

- **2026-05-07 (desktop)** — Slice S5: wire Repel impulse + Repellable
  marker (Slice 1 follow-up)
  ([physics-audit.md](./physics-audit.md))
- **2026-05-07 (desktop)** — Slice 9c-JitterTime: bomb-hit screen jitter
  ([settings-pipeline-slices.md](./settings-pipeline-slices.md))
- **2026-05-07 (laptop)** — Slice S1: drag → mphys native linear damping
  ([physics-audit.md](./physics-audit.md))
- **2026-05-07 (laptop)** — Slice S2: bomb recoil (`BombThrust`)
  ([physics-audit.md](./physics-audit.md))
- **2026-05-07 (laptop)** — Polish-bag: engine-tier scale calibration
  (S1-cal + S2-cal) ([physics-audit.md](./physics-audit.md))

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
