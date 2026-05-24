# Portal warp-back — completing the Portal item

Status: ready-for-human
Category: enhancement
Date: 2026-05-24

## Problem Statement

The Portal item is half-implemented. Placing a portal works end-to-end: a
player with a Portal in inventory drops a portal marker entity at their
position (`ConsumableSystem.createPortal` → `MapFactory.createPortal`), stamped
with a `Decay` deadline from `PortalConfig.activeTimeMs` (the Subspace
`WarpPointDelay` canon — *Portal point active time*). The marker spawns and,
when `Decay` fires, despawns.

**What's missing is the entire point of a portal: warping back to it.** There
is no "warp to my portal" action, no system that teleports the ship to its live
portal's position, and nothing that consumes the portal on use. The dropped
marker is inert. From the player's perspective the Portal item does nothing
useful — you drop a dot that vanishes.

This also blocks two bot behaviours in the [behaviour catalog](../bot-behaviour-catalog/PRD.md):
`choke / cut-off` (#12) and `repel/portal support` (#25) both assume a working
portal warp; `disengage` (#06) lists portal as an escape tool.

Repel — the sibling item this PRD was originally paired with — is **already
fully shipped** (config, applier, inventory, `ConsumableSystem` activation, and
`RepelSystem`'s radial-impulse effect). No repel work is in scope; it is the
reference pattern this PRD mirrors.

## Solution

A player (or bot) with a live portal triggers a warp action; the ship teleports
to the portal's position, subject to the same warp-gating Subspace applies to
all warps. The warp reuses the existing server-authoritative position-write
path (the same one `WarpPrizeApplier` / random-spawn warps use), so there is no
new teleport mechanism — only a new *source* of a warp (the player's own portal
position instead of a random spawn tile).

Mechanic (Subspace canon):
- A player carries Portal items (`InitialPortal` / `PortalMax` inventory, already wired via `Portal` component + `PortalSystem`).
- Dropping a portal consumes one Portal item and spawns the marker at the ship's position with `Decay = activeTimeMs` (placement already implemented; confirm the inventory decrement on placement).
- Warping to the portal teleports the ship to the live marker's position. One active portal per player — placing a new one replaces/expires the old.
- Warp is blocked for `AntiWarpSettleDelay` after any warp/portal/attach, and suppressed inside an enemy AntiWarp field (canon; AntiWarp's own runtime effect may itself be a gap — see Out of Scope).

## User Stories

1. As a player who dropped a portal, I want to warp back to it on command, so the item does what Subspace players expect.
2. As a player, I want my portal to expire after its active time, so I can't bank a permanent escape point.
3. As a player, I want warping to my portal blocked briefly after I just warped/attached, so warp-chaining is bounded (`AntiWarpSettleDelay`).
4. As a player with no live portal, I want the warp action to no-op (not crash, not warp to a stale point).
5. As a bot running `disengage` / `choke` / `repel-portal-support`, I want to trigger the portal warp through the same action a player uses, so there's no AI-only teleport path (ADR-0009 §3 input parity).

## Implementation Decisions

- **`WarpToPortal` action / intent.** A player input (portal-warp HOT key) and the equivalent bot intent emit a request to warp to the player's own live portal. Mirror `ConsumableSystem`'s existing portal-*placement* input handling.
- **Canonical position writer.** The actual teleport goes through whatever system owns authoritative `BodyPosition` writes for warps today (the `WarpPrizeApplier` / random-spawn-warp path). This PRD adds a *portal-sourced* warp request to that writer; it does **not** introduce a second teleport path (one canonical writer per component, ADR-0001).
- **Find-my-portal.** The warp resolver finds the requesting player's live portal entity (owner + not-yet-`Decay`-expired). Use an `EntityContainer` / component query keyed on portal owner; no hand-rolled `Map<EntityId,…>` (entity-containers rule).
- **One portal per player.** Placing a portal expires any prior live portal for that player (drain the old `Decay` immediately or despawn). Keeps "warp to my portal" unambiguous.
- **Warp gating.** Respect `AntiWarpSettleDelay` (canon: time after warp/portal/attach those actions are blocked, 1/100s → ms at the loader boundary). Consume/decrement the portal on placement, not on warp (Subspace: you can warp to the same portal repeatedly until it expires — confirm against REFERENCE.md before finalizing).
- **No new config.** `PortalConfig.activeTimeMs` already carries the only tunable. `AntiWarpSettleDelay` is a `[Misc]` knob — wire it through the settings pipeline if not already (check the tracker).

## Testing Decisions

- Unit: warp resolver picks the requesting player's live portal; ignores expired / other-players' portals; no-op when none.
- Unit: `AntiWarpSettleDelay` gate blocks warp within the window, allows after.
- Unit: placing a new portal expires the prior one.
- Integration (manual launch, per the test-gap caveat): drop portal, fly away, warp back, confirm position + the `Decay` expiry removes the warp target.

## Out of Scope

- **Repel** — already shipped. Not touched.
- **AntiWarp runtime effect.** AntiWarp's toggle + energy drain are wired but its *suppress-nearby-warps* effect may itself be unimplemented (sibling gap to the fog-of-war findings). If so, portal-warp respects AntiWarp only once AntiWarp's field effect lands; track separately. This PRD does not implement AntiWarp's field.
- **Portal as a two-way / team portal.** Subspace portals are personal one-way return points. No team-portal or paired-portal mechanic here.
- **Wormhole / fixed map warp tiles.** Different mechanic (map-authored), not the Portal item.

## Further Notes

**Subspace canon** (REFERENCE.md): `WarpPointDelay` = Portal point active time (already → `PortalConfig.activeTimeMs`); `AntiWarpSettleDelay` = post-warp/portal/attach block window (`[Misc]`, 1/100s); `InitialPortal` / `PortalMax` = inventory (wired). Look up REFERENCE.md `## Misc` + per-ship inventory rows before finalizing the gating + consumption semantics.

**Anchors.** Position authority + single-writer per ADR-0001; input parity (bots use the player action) per [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md). No new ADR needed — this completes an existing mechanic within established patterns.

**Reference implementation.** `RepelSystem` + `RepelCountSystem` + `ConsumableSystem.createRepel` are the fully-wired sibling; portal-warp mirrors their shape (inventory system already exists as `PortalSystem`; the missing piece is the activation→effect, analogous to `RepelSystem`).
