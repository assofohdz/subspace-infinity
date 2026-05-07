# Multi-Machine Workflow

The user works on this repo from multiple machines (laptop + desktop).
Coordination happens through `git push`/`pull`, not through shared
filesystem state. This rule defines how to keep both machines in sync
without losing work or duplicating slices.

## Machine identity

Each Claude session stores its local machine's friendly name in
auto-memory at `machine_identity.md` (under
`~/.claude/projects/<this-project>/memory/`). The rule references it
generically as **{your machine}**.

**Bootstrap on a new machine:** if `machine_identity.md` is missing,
ask the user which machine you're on (`laptop` / `desktop` / a
custom name) and write it to memory before continuing. Don't guess
from `hostname` — let the user own the friendly name.

## Active-work tracker

[`.scratch/active-work.md`](../../.scratch/active-work.md) is the
**single coordination point across every work collection**. Per-machine
rows; each row references where the work itself is defined.

```
- **laptop** — Slice S1 (physics-audit.md); branch `slice/s1-damping`
- **desktop** — Slice 9c-JitterTime (settings-pipeline-slices.md);
  branch `slice/9c-jittertime`
- **laptop** — Backlog: radar current-vs-neighbor styling
  (refactor-backlog/BACKLOG.md); branch `chore/radar-styling`
```

One row per active claim; remove when the work lands. A machine may
have more than one row only if the claims are independent and small
(e.g., a tracker-only edit alongside a coding slice) — but WIP=1 per
workstream still applies.

**Why a top-level file** — work collections are plural (gameplay
slices, physics-audit S-slices, refactor-backlog items, polish bag,
ad-hoc). Embedding the active-work header in any one of them misses
the others. `active-work.md` lives at the `.scratch/` root and links
out to whichever collection defines the actual work.

Per-collection status markers (⏳ in `settings-pipeline-slices.md`,
✅/⏳/🔲 inline status in `physics-audit.md`'s S-list, etc.) stay
where they live. `active-work.md` is the "claimed by whom" layer;
the source files are the "what is the work" layer.

## Session-start checklist

1. **Read machine identity** from `machine_identity.md`. If missing,
   ask the user.
2. **`git pull --rebase`** on the current branch (usually `infinity`)
   before any other action. Trackers (`.scratch/*.md`) are the
   highest-conflict surface; pulling first turns "merge headache"
   into "no conflict."
3. **Check `active-work.md`**:
   - If **{your machine}** has a row → you're resuming. `git checkout
     <branch>`, `git pull --rebase`, continue.
   - If **{your machine}** has no row → you're starting fresh. Pick
     work from any collection (slices queue, physics-audit S-list,
     backlog, …) respecting WIP=1 per workstream, flip its in-source
     marker to ⏳ if applicable, add a row to `active-work.md` naming
     the branch you'll create + the source file the work lives in,
     commit + push to `infinity`, then `git checkout -b slice/<name>`
     and start work.

## Mid-session

- **Commit + push at every milestone.** Cheap insurance. The rule
  here is "no uncommitted state across a coffee break." If you have
  to stop, WIP commit + push.
- **Tracker updates land in the same commit as the code change**
  (existing CLAUDE.md rule #6/#7). Don't push the tracker flip
  separately from the work — leaves the kanban out of sync if the
  push fails or the work is reverted.

## Session-end checklist

- **Work landed** → flip in-source marker (⏳ → ✅ if the source uses
  one), **delete** the row from `active-work.md`, commit + push.
  (CLAUDE.md rule #8: delete, don't strikethrough.)
- **Work WIP** → leave the in-source marker; ensure your last commit
  is pushed. The `active-work.md` row stays — that's how the other
  machine knows it's claimed.

## Failure modes — what to specifically avoid

- **Uncommitted state overnight on one machine.** Reflexive WIP
  commit + push at session end, no exceptions.
- **Both machines editing `infinity` directly at the same time.**
  Branch-per-slice prevents this. Direct `infinity` commits should be
  limited to tracker-only changes (marker flips, Active-work header
  edits) and pushed immediately to minimize the diverge window.
- **Slice mid-flight handed off without push.** If you stop work
  expecting to resume on the other machine, the WIP commit must be
  pushed. Pull on the new machine before checking out the branch.
- **Tracker drift.** Both machines flipping markers in the same
  session creates conflicts. `active-work.md` is designed to surface
  contention *before* code is written: read it first.

## Concurrent work (both machines simultaneously)

Realistic ceiling: two parallel slices, picked so they don't touch
the same files. S1 (drag → damping; touches `PlayerDriver`,
`ShipConfig`, ships.groovy) and S2 (bomb recoil; touches weapon
appliers, `BombConfig`) are independent. S1 + S6 (per-ship Radius)
both touch `ShipConfig` — that's the conflict-prone shape; serialize
those.

Coordinate via `active-work.md`: if your machine wants work that
overlaps with what the other machine has claimed, pick something
different or wait.

## Why this rule lives in `.claude/rules/` not `MEMORY.md`

Auto-memory is per-machine — saving the workflow there means each
machine has its own copy that drifts. This file is checked into git
so both machines pull the same rule. Only the **machine identity**
(which machine am I on?) belongs in per-machine auto-memory; the
**workflow** belongs in the repo.

## Reference

- [`.scratch/active-work.md`](../../.scratch/active-work.md) — the
  per-machine claims tracker (single coordination point across all
  work collections).
- [`.scratch/settings-pipeline-slices.md`](../../.scratch/settings-pipeline-slices.md)
  — gameplay-pipeline kanban (one of several work collections).
- [`.scratch/physics-audit.md`](../../.scratch/physics-audit.md) —
  physics audit + S1–S10 follow-up list (another work collection).
- CLAUDE.md rules #6/#7/#8 — tracker hygiene (per-commit updates,
  delete-don't-strikethrough).
