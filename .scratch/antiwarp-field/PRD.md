# AntiWarp field effect — suppressing enemy warp / portal / attach

Status: ready-for-human
Category: enhancement
Date: 2026-05-24

## Problem Statement

AntiWarp is the last of the four status capabilities whose toggle is wired but
whose effect is absent (siblings Cloak/Stealth/XRadar are covered by the
[fog-of-war PRD](../fog-of-war/PRD.md) / [ADR-0017](../../docs/adr/0017-server-side-visibility-fog-of-war.md)).
`AntiwarpActive` toggles, `AntiwarpStats` (tier + energy drain) projects,
`AntiWarpPrizeApplier` grants it, and `StatusDrainSystem` drains energy while
active — but **an active AntiWarp ship does nothing.** Its purpose in Subspace
is a *field*: enemies near an active AntiWarp ship cannot warp, use a portal,
or attach. That field is unimplemented.

Two concrete gaps:

1. **No range.** `AntiwarpStats` carries only `statusTier` + `energyDrainPerSecond`. The Subspace range knob **`AntiWarpPixels`** is referenced in `ShipConfigBuilder` + `AntiWarpPrizeApplier` but never projected to a readable per-ship stat the field effect can consume.
2. **No suppression.** Nothing checks, when a ship warps / portals / attaches, whether it sits inside an enemy's active AntiWarp field. `WarpSystem` (canonical `WarpToChange` writer) executes warps unconditionally.

This blocks the behaviour-catalog `choke / cut-off` behaviour (#12), which uses
AntiWarp to deny enemy retreat, and leaves the `disengage` (#06) escape
calculus wrong (a bot thinks it can warp out when it can't).

## Solution

An active AntiWarp ship projects a circular field of radius `AntiWarpPixels`.
An **enemy** ship attempting to warp / use a portal / attach **while inside that
field is suppressed** — the action no-ops (canonical Subspace "antiwarped"
behaviour). Per Subspace canon (`AntiWarpPixels` — *"Anti-Warp range (enemy
must also be on radar)"*), the suppression applies only when the attempting
ship is **on the AntiWarp ship's radar** — i.e. a ship the antiwarper can't see
(e.g. Stealthed, beyond radar) is *not* suppressed. That radar gate is exactly
the radar channel ADR-0017's visibility resolver computes.

A single server-side `AntiwarpField` query answers "is ship S (at position P)
suppressed by an enemy AntiWarp field?" Every action that can be antiwarped
consults it before executing. The same query is a fact bots reason about
(can this enemy escape me? can I escape?).

## User Stories

1. As a player flying an active AntiWarp ship, I want nearby enemies unable to warp/portal/attach, so AntiWarp anchors a zone.
2. As a player trying to warp out next to an enemy AntiWarp, I want the warp suppressed, so AntiWarp is a real counter to escape.
3. As a Stealthed player off the antiwarper's radar, I want to still be able to warp, so the canonical "must be on radar" rule holds.
4. As a teammate of an AntiWarp ship, I want to warp/portal/attach freely inside its field, so it only hampers enemies.
5. As a bot running `choke` / `disengage`, I want my escape/denial scoring to know whether a ship is inside an enemy AntiWarp field, so I don't try to warp out of a trap (or I set one).
6. As a player, I want AntiWarp suppression enforced server-side, so a modified client can't warp anyway.

## Implementation Decisions

- **Project the range.** Add the AntiWarp field radius (from `AntiWarpPixels`, Subspace pixels → world units / tiles at the loader boundary) to the per-ship stat. Either extend `AntiwarpStats` with a `rangeWorld` field or add a sibling — confirm `AntiWarpPixels` is on `ShipConfig` (it's already in `ShipConfigBuilder`) and project it via `ShipStatusProjector` alongside the other status knobs.
- **`AntiwarpField` query (server).** A service answering `isSuppressed(actor, position) → boolean`: scan active-`AntiwarpActive` ships within `AntiWarpPixels` of `position` (mphys `BinIndex` broad phase), filter to enemies (different team), and require the actor be on that antiwarper's radar (ADR-0017 resolver; see fallback). Returns true if any qualifies.
- **Suppression at the action sites, not a new writer.** The query is consulted by each antiwarpable action *before* it commits — `WarpSystem` before emitting `WarpToChange`; the [portal-warp](../portal-warp/PRD.md) warp-back before teleporting; the [attach-system](../attach-system/PRD.md) before attaching. Suppressed → the action no-ops (no `WarpToChange` emitted). One query, three consumers; no change to the canonical writers (ADR-0001).
- **Radar gate via ADR-0017.** The "enemy must be on radar" rule reuses the [ADR-0017](../../docs/adr/0017-server-side-visibility-fog-of-war.md) visibility resolver's *radar channel* (does the antiwarper see the actor on radar?). **Fallback before ADR-0017 lands:** plain "actor within antiwarper's `RadarRange`" distance check; swap to the resolver when fog-of-war ships. Document the fallback as temporary.
- **Enemy-only, self/team-exempt.** Friendly and own warps are never suppressed. Team check via `Frequency`.
- **`AntiWarpSettleDelay` is separate.** The global post-warp/portal/attach block window (`[Misc]`, 1/100s → ms) is a different mechanic (already relevant to portal-warp) — this PRD is the *proximity field*, not the settle delay. Both gate the same actions; keep them distinct.
- **Bot fact.** Expose the `AntiwarpField` query to bot perception so the ADR-0016 `choke` behaviour can score "deny enemy retreat" and `disengage` can avoid warping into a trap. No new bot machinery — a read of the same query.

## Testing Decisions

- Unit (`AntiwarpField`): enemy inside radius + on radar → suppressed; outside radius → not; off-radar (stealth/beyond range) → not (canon); friendly inside field → not; antiwarper inactive / `statusTier == 0` → not.
- Unit (WarpSystem): warp suppressed inside enemy field emits no `WarpToChange`; allowed otherwise.
- Integration (manual launch, per the test-gap caveat): two enemies, one with AntiWarp active — confirm the other cannot warp inside the radius, can outside.
- Bot: `disengage` does not select a warp-escape goal while inside an enemy AntiWarp field.

## Out of Scope

- **Cloak/Stealth/XRadar effects** — [fog-of-war PRD](../fog-of-war/PRD.md) / ADR-0017. This PRD only consumes the radar channel they define.
- **`AntiWarpSettleDelay`** global block window — separate knob; wire alongside portal-warp if not already.
- **Attach mechanic itself** — owned by [attach-system](../attach-system/PRD.md); this PRD only adds the suppression check the attach path must call.
- **Client feedback / "Antiwarped" message styling** — presentation follow-up; the mechanic (action no-ops server-side) is the scope.

## Further Notes

**Subspace canon** (REFERENCE.md): `## Toggle` **`AntiWarpPixels`** — *Anti-Warp range (enemy must also be on radar)*; per-ship `AntiWarpStatus` (0..2) + `AntiWarpEnergy` drain (wired); `[Misc]` `AntiWarpSettleDelay` — post-warp/portal/attach block (1/100s). Verify the pixels→world-unit conversion + the radar-gate semantics in REFERENCE.md before finalizing.

**Anchors.** Suppression consults a query and no-ops the action — no new canonical writer (ADR-0001); warp execution stays `WarpSystem`'s. Radar gate reuses [ADR-0017](../../docs/adr/0017-server-side-visibility-fog-of-war.md). Server-authoritative ([ADR-0005](../../docs/adr/0005-layered-architecture.md)). No new ADR — this completes an existing mechanic within established patterns, like [portal-warp](../portal-warp/PRD.md).

**Closes the status-capability layer.** With this + fog-of-war, all four status capabilities (Cloak, Stealth, XRadar, AntiWarp) have real effects; the dead-toggle family is fully retired.

**Reference implementation.** Mirrors the [portal-warp](../portal-warp/PRD.md) "complete the wired-but-inert mechanic" shape; the `AntiwarpField` proximity query parallels `RepelSystem`'s in-radius body scan (the shipped sibling).
