# ADR 0017 — Server-side visibility & fog-of-war

**Status:** Proposed
**Date:** 2026-05-24
**Deciders:** Asser Fahrenholz

## Context

Three ship capabilities — **Cloak**, **Stealth**, **XRadar** — are fully wired through the settings pipeline, prize appliers, per-ship tri-state `*Status` (0..2) knobs, the `*Active` toggles, and `StatusDrainSystem` energy drain, yet produce **no gameplay effect**. The toggles flip and drain energy; nothing reads them. There is no visibility model for them to modify. The [fog-of-war PRD](../../.scratch/fog-of-war/PRD.md) scopes the fix; this ADR settles the architectural decision the PRD depends on.

Today every client receives **every** entity unfiltered. The relevant transport facts:

- **Two sync channels.** Ship spatial state flows over **SimEthereal** (`EtherealHost` + per-connection `NetworkStateListener`, zone-interest bounded). Entity *components* flow over **Zay-ES** (`EntityDataHostedService` → `HostedEntityData`).
- **A per-connection component filter already exists and is live.** `GameSessionHostedService` registers `hed.registerComponentVisibility(new BodyVisibility(ethereal.getStateListener(conn)))` — a SiO2 `ComponentVisibility` that withholds the `BodyPosition` component from a connection unless SimEthereal's zone interest says it's nearby. Per-observer withholding is therefore a **proven, in-tree mechanism**, not a new capability to invent.
- **`RadarRange`** projects from `ShipConfig` to a component, but is consumed only client-side to size an off-screen radar camera frustum (a paging optimization) — it gates nothing on the wire.
- **`Occluder`** (line-of-sight blocker marker) exists and is consumed by **nothing**.

Subspace canon (REFERENCE.md `## Radar`, per-ship status rows): Cloak hides a ship **visually** (screen), Stealth hides it from enemy **radar**, XRadar lets the observer **see enemies on radar / through walls**; all three are tri-state per ship with a `*Energy` drain while active. `RadarMode` 3/4 add see-team variants.

This is a server-authority question ([ADR-0005](./0005-layered-architecture.md)): if the client decides what's hidden, a modified client trivially reveals cloaked ships. The decision (2026-05-24) is to enforce **server-side**.

### What this ADR does not settle

- **The radar HUD / `RadarMode` view styling** — client presentation; this ADR delivers *what* is visible, not how the radar is drawn.
- **AntiWarp's field effect** — sibling dead-toggle gap (suppress-nearby-warps), not visibility. Tracked with [portal-warp](../../.scratch/portal-warp/PRD.md).
- **Spectator visibility** (`NoXRadar`, `HideFlags`) — follow-up once in-play visibility lands.
- **The exact balance knobs** (cloak reveal radius, flicker duration) — tuning, via the settings pipeline.

## Decision

**Visibility is computed server-side, per observer, and enforced by withholding entities the observer must not see — never by sending-then-hiding. Enforcement extends the existing `ComponentVisibility` mechanism (the proven `BodyVisibility` hook) so the per-connection component filter becomes capability-, team-, and line-of-sight-aware, rather than only zone-interest-aware. Visibility resolves on two channels — visual (Cloak) and radar (Stealth / XRadar) — each bounded by the observer's reach. Bot perception reads the same resolver, so a bot sees exactly what its ship could see.**

### Two-channel visibility model

A per-`(observer, target)` resolution yields two booleans:

- **`visualVisible`** — renders on screen. Bounded by visual range + line-of-sight (`Occluder`). **Cloak** removes the target from this channel for observers beyond a short *reveal radius*; firing republishes it briefly (decloak flicker).
- **`radarVisible`** — appears as a radar blip. Bounded by the observer's `RadarRange`. **Stealth** removes the target from this channel for enemies; **XRadar** on the observer extends the channel (reveal enemies on radar, through occluders).

Team members are mutually visible on both channels (subject to `RadarMode` see-team variants). Own ship is always visible to its owner.

### Enforcement: extend `ComponentVisibility`, withhold on both channels

```
per connection (observer):
  visibility resolver decides, for each candidate target:
     visualVisible?  radarVisible?
  ComponentVisibility withholds the target's synced components
     (BodyPosition + render/blip components) when neither channel admits it
  SimEthereal zone interest remains the broad phase (distance bound)
```

The proven `BodyVisibility` already withholds `BodyPosition` per connection by zone interest; fog-of-war replaces that single predicate with the two-channel resolver. Because the client renders ships from the synced component, withholding the component **is** the hide — cheat-proof, since the data never reaches the client.

### Per-observer visibility resolver

A server-side service computes visibility. Inputs: observer `RadarRange` + `XRadarActive` + team; target position + `CloakActive` / `StealthActive` + team; `Occluder` tiles for LOS. Output: `{visualVisible, radarVisible}` per target. The resolver is the single source of truth consumed by **both** the `ComponentVisibility` filter (network) **and** the bot perception service (AI) — no second visibility code path.

**Broad phase reuses existing spatial structures.** Visibility is only computed for targets within the observer's max reach, using the SimEthereal zone interest / mphys `BinIndex` as the broad phase, so cost is `O(observers × nearby-targets)`, not `O(observers × all-targets)`.

### Bot parity

Bot perception (bot-AI v1) currently queries a `RadarRange`-radius `BinIndex` sweep. It instead queries the resolver for "what can ship S see," so bots inherit fog-of-war: no x-ray vision, cloaked enemies are invisible to bots beyond reveal radius, and the [ADR-0016](./0016-bot-behaviour-catalog.md) `concealment` / `intel_gap` / `los` inputs and the `scout` / `anti-stealth-hunt` behaviours become real (a bot *infers* a cloaker from kills/disappearances rather than seeing it). This is the same input-parity stance as [ADR-0009](./0009-bot-ai-architecture.md).

### Capability semantics (from REFERENCE.md)

- Tri-state `*Status` (0=forbidden / 1=acquirable / 2=starts-active) and `*Energy` drain already projected + drained — read, not re-spec'd.
- **Cloak** → visual-channel suppression + decloak-on-fire flicker (a `Decay`-shaped reveal window, [ADR-0007](./0007-entity-ttl-decay.md)).
- **Stealth** → radar-channel suppression vs enemies.
- **XRadar** → radar-channel extension for the observer.

## Consequences

**Positive.**
- Cloak, Stealth, XRadar gain real effect from one model; three dead prizes come alive at once.
- Cheat-proof: hidden entities are never transmitted; a modified client cannot reveal them. Honours server authority (ADR-0005).
- One resolver feeds both network filtering and bot perception — players and bots share a single visibility truth, no AI x-ray.
- Builds on a **proven in-tree mechanism** (`ComponentVisibility`/`BodyVisibility`), not a new transport.
- Unblocks behaviour-catalog `scout` (#26) + `anti-stealth-hunt` (#27) and gives `RadarRange` and `Occluder` a runtime consumer.

**Negative / costs.**
- Per-observer visibility is more server CPU than broadcast-all; mitigated by the spatial broad phase, but it is real per-tick cost that must be profiled at 32-player load.
- Couples the network layer to a gameplay resolver — the `ComponentVisibility` predicate is no longer trivial. Kept manageable by isolating all logic in the resolver service.
- Reveal/flicker churn: rapidly toggling component visibility (decloak flicker, edge-of-radar jitter) creates add/remove sync traffic; needs hysteresis / debounce to avoid blip flicker.
- LOS raycasts (`Occluder`) add cost; phased out of the first cut.

## Alternatives considered

- **Client-side render filtering** (server sends all; client hides). Rejected 2026-05-24 — the data is on the client, so cloak/stealth are trivially defeated by a modified client; violates server authority.
- **Hybrid** (sync-filter cloak/stealth, render-filter the rest). Rejected as the default — more moving parts for little gain once we accept that the proven `ComponentVisibility` hook already does server-side withholding cheaply.
- **A bespoke per-entity interest layer parallel to SimEthereal/Zay-ES.** Rejected — reinvents the `ComponentVisibility` mechanism that already exists and works.

## Resolved decisions (TL;DR)

- Enforce visibility **server-side** by withholding entities — not client-side hiding.
- Reuse + extend the existing `ComponentVisibility` (`BodyVisibility`) per-connection filter; SimEthereal zone interest stays the broad phase.
- Two channels: **visual** (Cloak) and **radar** (Stealth / XRadar), each reach-bounded.
- **One resolver** feeds both network filtering and bot perception (parity).
- LOS via `Occluder` is **phased** (radar-range model first, LOS second).

## Open work

- **SimEthereal position-channel spike (primary risk).** Confirm that withholding the `BodyPosition` *component* via `ComponentVisibility` fully hides a ship, or whether SimEthereal's own object/zone interest also streams position that must be withheld per-observer. The proven `BodyVisibility` suggests component-withholding is sufficient (it's how nearby-culling already works), but verify against SimEthereal source before implementing cloak. Use the `dependency-sources` skill.
- **XRadar-vs-Stealth interaction cell.** Does XRadar reveal a Stealthed enemy on radar? REFERENCE.md doesn't state it unambiguously; resolve against canon (and a quick check of community behaviour) before locking the resolver's truth table.
- **Performance validation** at 32 players × N targets; tune the broad-phase bound and the visibility recompute cadence (likely a Tier-3 field cadence, [ADR-0016](./0016-bot-behaviour-catalog.md), not per tick).
- **Flicker hysteresis** for decloak + radar-edge churn.
- **Team / `RadarMode` see-team variants** (modes 3/4) mapping into the resolver.
- **New visibility knobs** (cloak reveal radius, flicker duration) as a `*Config` via the settings pipeline ([ADR-0004](./0004-settings-pipeline.md)); verify canon names/units in REFERENCE.md.

## References

- [fog-of-war PRD](../../.scratch/fog-of-war/PRD.md) — implementation scope this ADR anchors.
- [ADR-0005](./0005-layered-architecture.md) — server authority / client-read-only (the principle this enforces).
- [ADR-0009](./0009-bot-ai-architecture.md) — bot input/perception parity (bots read the same resolver).
- [ADR-0016](./0016-bot-behaviour-catalog.md) — `concealment` / `intel_gap` / `los` inputs + `scout` / `anti-stealth-hunt` behaviours unblocked here.
- [ADR-0007](./0007-entity-ttl-decay.md) — `Decay`-shaped decloak-flicker reveal window.
- `GameSessionHostedService` (`registerComponentVisibility(new BodyVisibility(...))`), `ZoneNetworkSystem`, `EtherealHost` — the in-tree integration points.
- REFERENCE.md `## Radar` + per-ship `CloakStatus` / `StealthStatus` / `XRadarStatus` / `*Energy` rows.
