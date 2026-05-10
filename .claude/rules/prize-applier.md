# Prize Applier Implementation

Before implementing or modifying a prize applier in
`infinity-server/src/main/java/infinity/systems/ship/applier/`, **look up the
prize in [`REFERENCE.md`](../../.scratch/subspace-ini-reference/REFERENCE.md)
to find the canonical Subspace logic.** REFERENCE.md is the
Subspace VIE / Continuum settings spec; it is the source of truth for
how each prize is supposed to behave, what knobs tune it, and what
units those knobs use.

## What to look up

For each prize name (the applier's class prefix), check:

1. **Dedicated section** — e.g. `## Repel`, `## Shrapnel`, `## Brick`,
   `## Rocket`, `## Burst` — gives the per-prize tunables
   (`RepelTime`, `RepelDistance`, `ShrapnelRate`, `BrickTime`, etc.)
   and their units (centiseconds, pixels, percentages-of-1000).
2. **`## PrizeWeight`** — confirms the prize is a real Subspace prize
   type and shows its family grouping.
3. **`## Prize`** — global prize-system knobs (`PrizeFactor`,
   `PrizeDelay`, `MultiPrizeCount`, `PrizeNegativeFactor`).
4. **`## Cost`** — per-prize point cost (Subspace shop mechanic).
5. **Per-ship section** — `*Status` (0..2 tri-state for AntiWarp /
   Cloak / Stealth / XRadar capability), `*Energy` (drain rate while
   active), `Initial*` / `*Max` for inventory-style prizes.

## How to apply

- **Match the canonical knob names + units** when adding fields to
  `*Config` records or components. `RepelTime` is centiseconds in
  Subspace; convert to ms (×10) at the loader boundary, not at the
  consumer.
- **Status family appliers** (AntiWarp, Cloak, Stealth, XRadar) — the
  `*Status` ship setting is a tri-state (`0` = forbidden, `1` =
  acquirable via prize, `2` = starts active). Apply respects that
  signal: if `*Status == 0`, the applier no-ops (Subspace behaviour).
- **Energy-drain capabilities** (Cloak, Stealth, XRadar, AntiWarp)
  drain at `*Energy / 1000` per centisecond while active. Drain runs
  even when the ship is at 0 health pool — Subspace canonical.
- **If Infinity diverges intentionally** — e.g. simplified or removed
  a Subspace mechanic — document the deviation in the applier's class
  Javadoc with the REFERENCE.md section it differs from. Silent
  divergence produces "Subspace players expect X, Infinity does Y"
  bugs that are hard to triage.

## Why

Reinventing prize semantics from the applier name alone reproduces the
"random Java placeholder" problem that Phase B is closing for tuning
knobs. REFERENCE.md exists specifically because the operator-facing
Groovy fragments use Subspace-canonical key names, units, and tri-state
encodings — the appliers should match.

## Reference

- [`REFERENCE.md`](../../.scratch/subspace-ini-reference/REFERENCE.md) — Subspace settings spec, indexed by section.
- [`config-pattern.md`](./config-pattern.md) — template-vs-instance Pattern 4 (where the per-prize knobs live in the typed pipeline).
