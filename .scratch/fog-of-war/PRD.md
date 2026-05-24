# Fog-of-war & visibility model — giving Cloak / Stealth / XRadar their effect

Status: ready-for-human
Category: enhancement
Date: 2026-05-24
Anchor: [ADR-0017 — Server-side visibility & fog-of-war](../../docs/adr/0017-server-side-visibility-fog-of-war.md)

## Problem Statement

Three ship capabilities are wired through config + prize + toggle + energy
drain but produce **no actual effect**, because there is no visibility model
for them to modify:

- **Cloak** — `CloakActive` toggles + drains energy, but the ship stays fully visible.
- **Stealth** — `StealthActive` toggles + drains energy, but the ship stays on everyone's radar.
- **XRadar** — `XRadarActive` toggles + drains energy, but the flag **is never read by any system**.

Today every client receives **every** entity from the server unfiltered via
SimEthereal; the client renders all of them. `RadarRange` only sizes a
client-side off-screen camera frustum (a paging/culling optimization), and the
`Occluder` line-of-sight marker component exists but is **consumed by nothing**.
There is no server-side visibility gating of any kind.

The consequence: the entire stealth/detection layer of Subspace gameplay is
absent, and three prizes are dead. It also blocks bot behaviours
`spot / scout` (#26) and `anti-stealth-hunt` (#27) in the
[behaviour catalog](../bot-behaviour-catalog/PRD.md), and the `concealment`,
`intel_gap`, and `los` inputs in [ADR-0016](../../docs/adr/0016-bot-behaviour-catalog.md)'s
vocabulary have nothing real to read.

## Solution

A **server-side, per-observer visibility model** decides what each connection
may see, and SimEthereal simply does not send entities the observer shouldn't
know about. Visibility is enforced where it's authoritative and cheat-proof
(decided 2026-05-24): a cloaked ship that shouldn't be visible is **never
transmitted** to that client, so a modified client cannot reveal it. This
matches server-authority / client-read-only ([ADR-0005](../../docs/adr/0005-layered-architecture.md)).

Integration point already exists: `GameSessionHostedService` registers
`registerComponentVisibility(new BodyVisibility(ethereal.getStateListener(conn)))`
per connection. Fog-of-war extends this `ComponentVisibility` / per-connection
`NetworkStateListener` interest mechanism with a real visibility resolver,
rather than inventing a new sync path.

**Two visibility channels** (matching Subspace canon):

- **Visual channel** — what renders on screen. Range-limited; **Cloak** removes a ship from the visual channel for observers beyond a short reveal distance (with the canonical decloak-on-fire flicker).
- **Radar channel** — what appears as a radar blip. Bounded by `RadarRange`; **Stealth** removes a ship from enemies' radar channel; **XRadar** extends/reveals the observer's radar channel (see enemies on radar, see through walls).

Each `(observer, target)` pair resolves to "visible on visual? / visible on
radar?" from: distance vs the observer's radar/visual reach, target's
Cloak/Stealth toggles, observer's XRadar toggle, team/frequency, and
`Occluder` line-of-sight. The server filters sync accordingly.

**Bot parity.** Bot perception reads the *same* visibility model — a bot only
"sees" what the visibility resolver says its ship can see. This unifies bot
perception with player visibility (no AI x-ray vision) and makes the ADR-0016
`concealment` / `intel_gap` / `los` inputs real. This is the substrate the
bot-AI v1 PRD already gestured at ("perception radius = ship's `RadarRange`").

## User Stories

1. As a player flying a Cloak ship, I want enemies to not see me on screen until I'm close or I fire, so cloak is a real stealth tool.
2. As a player flying a Stealth ship, I want to not appear on enemy radar, so I can flank unseen.
3. As a player with XRadar active, I want to see enemy ships on my radar (and through walls), so XRadar is worth its energy drain.
4. As a player without XRadar, I want enemies beyond my radar range / behind occluders to be hidden, so the arena has fog-of-war.
5. As a competitive player, I want cloak/stealth enforced server-side, so a modified client can't reveal hidden ships.
6. As a bot running `scout` / `anti-stealth-hunt`, I want my perceived enemy set to match what my ship could actually see, so AI detection is skill-fair and the `intel_gap` / `concealment` inputs are meaningful.
7. As a player, I want a cloaked ship to flicker into view briefly when it fires, so cloak has the canonical risk.

## Implementation Decisions

- **Visibility resolver (api/ + server).** A server-side service computes per-observer visibility. Inputs: observer `RadarRange` + `XRadarActive`, target position + `CloakActive` / `StealthActive`, team/frequency, and `Occluder` LOS. Output per target: `{visualVisible, radarVisible}`. Pure-ish + unit-testable; the network layer consumes it.
- **Enforcement via `ComponentVisibility` / SimEthereal interest.** Extend the existing `BodyVisibility` (`GameSessionHostedService`) so a connection's `NetworkStateListener` is fed only the entities the resolver marks visible to that connection. Withhold, don't blank — the entity is absent from the stream, not zeroed. SimEthereal's zone interest already bounds by distance; this adds the capability/LOS/team filter on top. (Confirm against the [sim-ethereal skill] how per-connection object interest is currently scoped.)
- **Cloak = visual-channel suppression.** Beyond a reveal radius, a cloaked enemy is withheld from the visual stream. Decloak-on-fire: firing republishes the ship to the visual channel for a short window (a `Decay`-shaped reveal, per [ADR-0007](../../docs/adr/0007-entity-ttl-decay.md)).
- **Stealth = radar-channel suppression.** A stealthed enemy is withheld from the radar blip stream (but still visible on the visual channel within visual range). XRadar on the observer overrides.
- **XRadar = radar-channel extension.** Reveals enemy radar blips within an extended reach and through occluders. Whether XRadar defeats Stealth is a **canon detail to verify in REFERENCE.md `## Radar` / per-ship `XRadarStatus`** before finalizing the interaction matrix — do not guess.
- **LOS via `Occluder`.** Consume the existing `Occluder` marker for line-of-sight: a target behind an occluder is not visually visible (one Bresenham / raycast against occluder tiles). Phase this — a radar-range-only model can ship first, LOS second (see Phasing).
- **No new prize/config/toggle tiers.** Cloak/Stealth/XRadar prize appliers, toggles, energy drain, and per-ship `*Status` tri-states already exist. This PRD reads them; it does not re-spec them. Any *arena-global* visibility knobs (reveal radius, cloak-flicker duration) are new `*Config` records via the settings pipeline ([ADR-0004](../../docs/adr/0004-settings-pipeline.md)) — verify Subspace canon names/units in REFERENCE.md first.
- **Bot perception adapter.** The bot perception service (bot-AI v1) queries the same resolver for "what can ship S see," replacing/▷constraining its current `RadarRange`-radius query. Keeps player + bot symmetric.

## Testing Decisions

- Unit (resolver): cloaked target hidden beyond reveal radius, visible within; stealthed target off radar but on-screen within visual range; XRadar observer sees stealthed/distant enemies on radar; team members always mutually visible; occluder blocks visual LOS.
- Unit (decloak): firing reveals on the visual channel for the flicker window, then re-hides.
- Integration (manual launch, per the test-gap caveat): two clients, one cloaked — confirm the cloaked ship is absent from the other client's entity stream (not merely unrendered), proving server-side enforcement.
- Bot: a bot's perceived-enemy set excludes ships its ship couldn't see; `anti-stealth-hunt` only triggers when a cloaker is *inferred* (kills/disappearances), not directly seen.

## Out of Scope

- **AntiWarp field effect.** AntiWarp's toggle/drain are wired but its suppress-nearby-warps effect is a separate gap (surfaced alongside these findings). Not visibility; tracked separately (intersects [portal-warp](../portal-warp/PRD.md)).
- **Radar HUD redesign.** `RadarMode` (0..4 quarters/half views) and radar rendering polish are client-presentation; this PRD delivers *what* is visible, not the radar UI styling.
- **Spectator visibility (`NoXRadar` for specs).** Spectator-mode visibility rules are a follow-up; this PRD covers in-play ships.
- **Mines on radar (`SeeMines`).** Per-ship mine-visibility flag; small follow-up once the radar channel exists.

## Further Notes

**Architectural decision captured in [ADR-0017](../../docs/adr/0017-server-side-visibility-fog-of-war.md)**
(Proposed, 2026-05-24): enforce server-side by withholding entities at the
existing `ComponentVisibility` (`BodyVisibility`) hook; two-channel model
(visual = Cloak, radar = Stealth/XRadar); one resolver feeds both network
filtering and bot perception. **Before implementing**, clear the ADR's Open
Work — chiefly the SimEthereal position-channel spike (confirm component
withholding fully hides a ship) and the XRadar-vs-Stealth interaction cell.
This PRD is the scope; ADR-0017 is the rationale anchor.

**Subspace canon** (REFERENCE.md): `## Radar` `RadarMode` (0..4); per-ship
`StealthStatus` / `CloakStatus` / `XRadarStatus` (0..2 tri-state) +
`*Energy` drain (wired); `SeeMines`; `NoXRadar` (spectator). Verify the
Cloak/Stealth/XRadar interaction matrix + reveal/flicker units in REFERENCE.md
`## Radar` and the per-ship sections before finalizing — the section header
doesn't tell you the interaction rules.

**Anchors.** Server authority / client-read-only ([ADR-0005](../../docs/adr/0005-layered-architecture.md));
TTL via `Decay` for the decloak-flicker window ([ADR-0007](../../docs/adr/0007-entity-ttl-decay.md));
settings pipeline for any new visibility knobs ([ADR-0004](../../docs/adr/0004-settings-pipeline.md));
bot input/perception parity ([ADR-0009](../../docs/adr/0009-bot-ai-architecture.md)).

**Unblocks.** Cloak + Stealth + XRadar gain real effects; behaviour-catalog
`spot / scout` (#26), `anti-stealth-hunt` (#27); and the `concealment` /
`intel_gap` / `los` inputs in ADR-0016.
