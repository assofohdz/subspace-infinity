# Legacy Subspace → Infinity — behavioural divergence tracker

Map of places where **Subspace Infinity** intentionally diverges from the
legacy **Subspace VIE / Continuum** behaviour a player would notice.
Scope is **behavioural** — what does a Subspace player encounter that
feels different in Infinity? Architectural divergences (Continuous +
Stats split, Config-Component Projection, layered modules, RaM, etc.)
live in [`docs/adr/`](../docs/adr/), not here. Per-key tuning conversions
(centisecond → millisecond, pixels → tiles, status tri-state encoding)
live in [`settings-pipeline.md`](./settings-pipeline.md).

The canonical Subspace reference is
[`subspace-ini-reference/REFERENCE.md`](./subspace-ini-reference/REFERENCE.md).
When you encounter or introduce a deliberate divergence, add a row here in
the same change — same discipline as rule #5 / #6 for `.scratch/*.md`
trackers (per [CLAUDE.md](../CLAUDE.md)). Delete a row when the divergence
is either reconciled (Infinity matches canon) or formalised into an ADR
that supersedes this entry.

Columns: **canon** = what Subspace players expect; **Infinity** = what
Infinity does; **why** = the reason (constraint, bug, simplification,
explicit design); **where** = the file or doc; **status** = `stable` /
`temporary` / `intentional`.

---

## Mechanics

| canon | Infinity | why | where | status |
|---|---|---|---|---|
| Gametypes are fixed server binaries (Trench, KOTH, Jackpot, …); arena binds to one | Arenas compose orthogonal modules (scoring + winCondition + mechanic + …); gametypes are emergent names | Per-arena variety without forking the server | [ADR-0008](../docs/adr/0008-arena-composition-and-modules.md) | intentional |
| Flags / goals / game elements are baked into arena init | Map emits `Spawned`-marked transient entities; mechanic modules drain them on `onArenaLoad` and add the canonical state components | Same map can hand its game elements to whichever mechanic is loaded; map = entity lifecycle, mechanic = state | [ADR-0008](../docs/adr/0008-arena-composition-and-modules.md) § Map-loaded game-element drain | intentional |
| Per-arena cumulative score (single tier) | Three-tier explicit score: `*RoundScore` (resets on round end), `*MatchScore` (resets on match end; omitted entirely for `matchStructure 'continuous'`), `*TotalScore` (accumulating) — across player / team / arena scopes | Round-based gametypes need a place to put per-round scores without erasing session totals | [ADR-0008](../docs/adr/0008-arena-composition-and-modules.md) § Three-tier score model | intentional (F2+) |
| Round-end is hardcoded per gametype | `winCondition` modules expose two roles: optional `checkTermination` (terminator) + required `declareWinner` (decider). Terminator triggers OR; decider votes first-non-UNDECIDED-wins | Composable round logic across gametypes without forking | [ADR-0008](../docs/adr/0008-arena-composition-and-modules.md) § Win condition two-role | intentional (F2+) |
| WARP prize warps to a random arena spawn point | WARP prize warps the recipient to arena center | `WarpSystem` only exposes `warpToCenter` today; multi-spawn-point selector not yet implemented | [`WarpPrizeApplier.java`](../infinity-server/src/main/java/infinity/systems/ship/applier/WarpPrizeApplier.java) | **temporary** |
| `EnterShipEnergy=100%` setting (canon `[Misc]`) gates ship swap by current energy ≥ that fraction of max | Infinity hardcodes the gate to "current Energy ≥ `EnergyStats.max`"; no per-arena percentage knob | Avoid letting a half-energy switch happen mid-engagement; no `EnterShipEnergy` key in `REFERENCE.md` to hang the value on | [`AvatarSystem.java`](../infinity-server/src/main/java/infinity/systems/AvatarSystem.java) | intentional |
| Negative prizes (`PrizeNegativeFactor` 1-in-N roll) run inverse-stat appliers (downgrade weapon, decrement inventory) | Negative-prize slot substitutes DUD instead of running inverse appliers | Inverse-stat appliers not implemented; canonical probability preserved via the same knob so a future swap is in-place | [`PrizeConfig.java`](../api/src/main/java/infinity/config/PrizeConfig.java) | **temporary** |
| Thor projectile is a hardcoded weapon with canonical tunables in `[Bomb]`-shape settings | Thor has its own `[Thor]` section + `thor.groovy` adapter; no Subspace canonical knobs to inherit | Thor's projectile shape diverges enough from bombs to deserve its own typed surface | [`ThorConfig.java`](../api/src/main/java/infinity/config/ThorConfig.java), [`ThorAdapter.java`](../infinity-server/src/main/java/infinity/settings/ThorAdapter.java) | intentional |
| Subspace canon scopes ship-death events to player-controlled ships (mobs/AI ignored) | `PlayerKilledEvent` + `PrizeSpawnIntent` fire for ANY ship death — bots count as kill targets in the arena-modules scoring pipeline (`KillPointsScoring`) and drop prizes too | Arena-module smoke testing needs bot kills to flow end-to-end; easier to scope by consumer than to maintain a player-only gate at the emitter | [`EnergySystem.handleDeath`](../infinity-server/src/main/java/infinity/systems/ship/EnergySystem.java) | intentional |

## Tuning surface

| canon | Infinity | why | where | status |
|---|---|---|---|---|
| Friendly-fire is encoded as separate per-weapon flags | Single `friendlyFire` tri-state (`0`=off, `1`=bomb-splash-only, `2`=all weapons) | Simpler operator surface; covers the FF regimes arena-ops actually request | [`ArenaConfig.java`](../api/src/main/java/infinity/config/ArenaConfig.java) | intentional |
| No drag — ships coast indefinitely | `linearDamping` per ship; mphys applies `velocity *= pow(damping, t)` per tick (`1.0` = no damping; `0.99` ≈ 1%/sec loss) | Stops infinite drifters; gives ops a "ship-feel" knob | [`ShipConfig.java`](../api/src/main/java/infinity/config/ShipConfig.java) | intentional |
| `[Misc]` `WarpRadiusLimit` = random spawn-distance limit from arena center (per `REFERENCE.md` §Misc) | Used as legacy-fallback radius for the WARP prize | Pending the `WarpSystem` rewrite that will switch WARP to multi-spawn-point selection; field meaning then re-anchors to canon | [`WarpPrizeApplier.java`](../infinity-server/src/main/java/infinity/systems/ship/applier/WarpPrizeApplier.java) | **temporary** |

## Physics

| canon | Infinity | why | where | status |
|---|---|---|---|---|
| Wall friction handled by the physics resolver (rigid-body friction at contact points) | `wallFriction` is applied directly to body **linear velocity** at the boundary, NOT via mphys's rigid-body friction | mphys's resolver-friction produces torque at off-center contact points, which would rotate ship heading toward the wall — wrong for arcade physics | [`ArenaConfig.java`](../api/src/main/java/infinity/config/ArenaConfig.java) | intentional |

## Live ops / hot-reload

| canon | Infinity | why | where | status |
|---|---|---|---|---|
| Most config changes require server restart | `arena.groovy` + preset fragments hot-reload via mtime polling; ship templates re-project to ECS components on save; modules can opt-in to live config updates via `Reloadable<C>` | Tighter operator iteration loop; admin doesn't have to kick the server to nudge balance | [ADR-0004](../docs/adr/0004-settings-pipeline.md), Slice F1 (`Reloadable<C>`) | intentional |
| Subspace canon chat is arena-scoped at the protocol level (each arena's public channel is isolated) | `ChatHostedPoster.postPublicMessage` is zone-wide — every connected client receives, regardless of arena | Arena-scoped chat broadcast not implemented yet; modules that want "tell everyone in MY arena" use `postPublicMessage` and rely on single-arena zone configurations | [`InfinityChatHostedService.postPublicMessage`](../infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java), [`TimedRoundStructure.announceTimeRemaining`](../infinity-server/src/main/java/infinity/modules/roundstructure/TimedRoundStructure.java) | **temporary** |
| Map change requires arena reload (kicks all players) | `~swapMap <file>` swaps the `.lvl` mid-arena without unloading the arena entity; player ships persist | Lower-friction map iteration during arena tuning | [`ArenaLogic.java`](../infinity-server/src/main/java/infinity/systems/ArenaLogic.java) `swapMap` | intentional |

---

## How to use this tracker

- **Add a row** whenever you encounter or introduce a deliberate
  divergence in a code change. Same commit as the code — do not defer.
- **Status conventions**:
  - `intentional` — the divergence is a design decision and will stay.
  - `temporary` — Infinity diverges because the canonical behaviour
    isn't implemented yet; the row should be deleted when canon is
    restored (or promoted to `intentional` if the team decides to keep
    the simplification).
  - `stable` — neither intentional nor temporary; just historically
    different and not currently scheduled to change.
- **Delete a row** when the divergence is reconciled (Infinity matches
  canon now) or when an ADR formalises the decision (cite the ADR in
  the row's `where`, then delete after landing the ADR).
- **Don't** put per-key tuning conversions here (cs→ms, pixels→tiles,
  status tri-state encoding) — those live in
  [`settings-pipeline.md`](./settings-pipeline.md) which has the per-key
  pipeline view.
- **Don't** put pure-architectural divergences here (ECS shape, RaM,
  layered modules, Config-Component Projection) — those live in
  [`docs/adr/`](../docs/adr/).
