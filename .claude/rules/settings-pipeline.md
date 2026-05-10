# Settings Pipeline — Look up Subspace canon first

Before authoring or modifying anything in the typed settings pipeline —
a Groovy `*Adapter` in `infinity.settings.*`, a `*Config` record in
`api/src/main/java/infinity/config/**`, or a runtime consumer that reads from
`ConfigRegistry` — **look up the Subspace key in
[`REFERENCE.md`](../../.scratch/subspace-ini-reference/REFERENCE.md)
first.** REFERENCE.md is the Subspace VIE / Continuum settings spec; it
tells you what the key *means*, what units it's in, and which section
(`[Misc]`, `[Bomb]`, `[Repel]`, …) it belongs to.

This rule generalizes the discipline already required by
[`prize-applier.md`](./prize-applier.md). That rule covers prize
appliers; this one covers the rest of the typed-settings code path so
the same lookup happens whether you're touching the loader, the typed
record, or the consumer side.

## What to look up

For each Subspace fragment key the change touches:

1. **The dedicated section** — `## Bomb`, `## Bullet`, `## Repel`,
   `## Mine`, `## Burst`, `## Brick`, `## Rocket`, `## Shrapnel`,
   `## Wormhole`, `## Door`, `## Radar`, `## Toggle`, `## Spectator`,
   `## Message`, `## Misc`, `## Spawn`, `## Owner`, `## Custom`,
   `## Prize`, `## PrizeWeight`, `## Cost`, `## DPrizeWeight`. The
   one-line description tells you what the key *does*.
2. **Units** — Subspace time fields are typically in centiseconds
   (cs); convert to ms (×10) at the loader boundary, never at the
   consumer. Distance fields are in Subspace pixels unless noted; tile
   counts are noted explicitly. Read the description to know which.
3. **Per-ship sections** — `[All]` / `[Warbird]` / etc. for per-ship
   `Initial*`, `*Max`, `*Status`, `*Energy`, `*Time`. These flow
   through `GroovyShipLoader` → `ShipConfig` → spawn projection, *not*
   through arena-global adapters.
4. **Cross-section references** — a `[Misc]` key often actually
   belongs to a different mechanic. Examples:
   - `WarpPointDelay` is *Portal point active time* (Portal mechanic).
   - `WarpRadiusLimit` is *Random spawn distance limit from arena
     center* (Spawn mechanic — not Portal, not Warp prize).
   - `DecoyAliveTime` is *Decoy active time* (Decoy mechanic).
   The `[Misc]` section header alone doesn't tell you which mechanic
   owns the key — REFERENCE.md does.

## How to apply

- **Match the canonical key name + units** in the typed `*Config`
  field. `WarpPointDelay` is centiseconds → `activeTimeMs` (cs×10),
  not `activeTimeCs`. Document the conversion in the record's Javadoc.
- **The slice description is not the spec.** When the slice queue or
  user message says "wire knob X" but REFERENCE.md says X belongs with
  a different mechanic, **surface the contradiction before coding** —
  don't silently pick one. The slice queue is a TODO list; REFERENCE.md
  is the source of truth.
- **If Infinity intentionally diverges** from REFERENCE.md — e.g. the
  consumer simplifies or replaces the canonical Subspace mechanic —
  document the deviation in the `*Config` record's class Javadoc with
  the REFERENCE.md section it differs from. Silent divergence
  produces "Subspace players expect X, Infinity does Y" bugs that are
  hard to triage.
- **Pair the lookup with [`config-pattern.md`](./config-pattern.md)
  and [`decay-ttl.md`](./decay-ttl.md)** — REFERENCE.md tells you what
  the value means; those rules tell you where it lives in the typed
  pipeline (template vs component; `Decay` for TTLs).

## Why

Slice scoping based on the key name alone has produced rework: a key
named `WarpRadiusLimit` was scoped into `PortalConfig` because "warp"
suggested portal-use, then reverted when REFERENCE.md was checked and
showed it's a spawn knob. Same risk for any `[Misc]` key — the section
header is a flat list, not a semantic grouping. Reading REFERENCE.md
first costs ~30 seconds and prevents whole-slice rescopes.

## Reference

- [`REFERENCE.md`](../../.scratch/subspace-ini-reference/REFERENCE.md) — Subspace settings spec, indexed by section.
- [`prize-applier.md`](./prize-applier.md) — narrower scope (prize
  appliers); same discipline.
- [`config-pattern.md`](./config-pattern.md) — template (`*Config`)
  vs instance (component) split; where the looked-up value lives.
- [`decay-ttl.md`](./decay-ttl.md) — TTL fields project to `Decay`,
  not parallel `*Time` components.
- [`.scratch/settings-pipeline.md`](../../.scratch/settings-pipeline.md) —
  per-key tracker; the columns mirror what you should verify in
  REFERENCE.md.
