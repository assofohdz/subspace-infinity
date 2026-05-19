# Player vs Ship identity split — implementation PRD

Status: ready-for-agent
Labels: area:lifecycle, area:client, framework
Triggered by: F2.6 smoke-test NPE — `ModelViewState.java:368` when the player dies without a respawn policy. Surfaced a deeper architectural issue: the client treats the **ship entity** as its identity, but the ship is transient (destroyed on death by `DeathSystem` → `Decay` → reaper).

## Background

Current state (post-F2.6):

- `playerEntityId` exists server-side ([`GameSessionHostedService.java:219`](../../infinity-server/src/main/java/infinity/server/GameSessionHostedService.java)). Created at session connect, holds only a `Name`. Durable across deaths.
- `avatarEntityId` is the **ship entity** ([same file:227-248](../../infinity-server/src/main/java/infinity/server/GameSessionHostedService.java)) created by `ShipFactory.createPlayerShip`. Holds `Player` marker, `ShipType`, physics, energy, weapon stats, `Frequency`, `ArenaId`, and a `Parent(playerEntityId)` link back to the durable player.
- RMI `GameSession.getAvatar()` returns the ship-entity-id; client treats this as its identity.
- On death: `DeathSystem` stamps `Decay(now, now)` → SiO2 reaper despawns the ship entity. `playerEntityId` survives, but the client doesn't track it. The client's `WatchedEntity` pointing at the now-removed ship entity fires `applyChanges()` returning `true`, then `entity.get(Frequency.class)` returns `null` → NPE.

The conceptual model the project actually wants:

> *Player* is the account/connection-bound entity — the person sitting in front of the screen pressing buttons. *Ship* is the entity the player is currently controlling — transient, recreated on spawn / death / ship-switch. Frequency (team membership) belongs to the **ship**, not the player — a player can hop between teams by changing arenas or by team-balance reassignment, and each respawn re-asserts the team.

## Scope

### In scope

- **P2** — Add `CurrentShip(shipEntityId)` component on the player entity. Maintained by the spawn / death lifecycle. No client read yet — pure server-side data wiring.
- **P4** — Switch client identity from ship-entity-id to player-entity-id. `GameSessionState.getAvatarEntityId()` returns the durable player id. Client states (`ModelViewState`, `RadarState`, `PositionHudState`, `JitterState`, `HudLabelState`, `PlayerListState`, `AvatarMovementState`, `InfinityCameraState`) resolve the current ship via `CurrentShip` lookup on the player. Ghost state (no current ship) is encoded by `CurrentShip` absence; ship-specific HUD hides gracefully — no NPE possible.
- **P5** — Rename the `Player` marker → `PlayerShip`. The current name is conceptually inverted (it marks the ship, not the player). Pure rename across ~10 sites.

### Out of scope (deliberately)

- **P1 (defensive null-guard)** — obviated by P4. The watch will be on a durable entity that doesn't get destroyed; the NPE is structurally impossible after P4 lands.
- **P3 (move Frequency to player)** — explicitly rejected. Per project owner: frequency = team membership = belongs to the ship. A player without a ship has no team; a respawn re-asserts team via the new ship's `Frequency`.
- **Persistent player identity across sessions** — `playerEntityId` is created at connect and destroyed at disconnect today. Cross-session durability is a separate concern (see [`account-system/`](../account-system/)).
- **Multi-ship-per-player** — `CurrentShip` is a single-cardinality link. Multi-ship piloting (if ever) requires a different data shape.

## Design

### Two-tier identity

| Tier | Entity | Lifetime | Holds |
|---|---|---|---|
| **Player (account)** | `playerEntityId` — created in `GameSessionImpl` constructor, destroyed at disconnect | One per connection, whole session | `Name`, `CurrentShip(shipEntityId)` when alive — absent when ghost |
| **Ship** | created by `ShipFactory.createPlayerShip` at first spawn or by future `RespawnPolicyModule` (F2 slice) | Transient — recreated per life | `PlayerShip` marker (renamed from `Player`), `ShipType`, all stats, `Energy`, `Frequency`, `BodyPosition`, `ArenaId`, `Parent(playerEntityId)`, etc. |

The ship→player link is the existing `Parent(playerEntityId)` (already set in `ShipFactory.createPlayerShip`). The player→ship link is the **new** `CurrentShip` component (only one new type needed).

### New component

```java
// api/src/main/java/infinity/es/lifecycle/CurrentShip.java
package infinity.es.lifecycle;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Player → ship reverse link. Present when the player is alive and piloting a ship;
 * absent when the player is a ghost (between death and respawn). Canonical writer:
 * {@code AvatarSystem} (or future {@code RespawnPolicyModule}). The forward link
 * (ship → player) is the existing {@code Parent} component on the ship.
 */
public final class CurrentShip implements EntityComponent {
  private final EntityId shipId;

  public CurrentShip() { this.shipId = null; } // serialization
  public CurrentShip(final EntityId shipId) { this.shipId = shipId; }
  public EntityId shipId() { return shipId; }
}
```

Per [`components.md`](../../.claude/rules/components.md): immutable, no-arg ctor, crosses the wire (client reads it) → register in `GameServer.registerSerializers()`.

### Canonical writer

`AvatarSystem` becomes the canonical writer of `CurrentShip` on the player entity:

- **On spawn / respawn** — when a ship entity is created for the player, stamp `CurrentShip(shipId)` on the player.
- **On ship-change** (Pattern-4 projection onto the same entity-id, current behaviour for `requestShipChange`) — no change, the ship-entity-id didn't change so the link is still valid.
- **On death** — when `DeathSystem` stamps `Dead`+`Decay`, also remove `CurrentShip` from the player. The player is now a ghost. The ship entity is despawned next tick by the reaper.

Per ADR-0001 (canonical writer rule): `AvatarSystem` is the only system that writes `CurrentShip`. Other systems that need to react to "player got a new ship" observe the component change via `EntitySet` / `WatchedEntity`.

### Client identity switch

`GameSessionState`:

```java
// before
avatarEntityId =
    getState(ConnectionState.class).getService(GameSessionClientService.class).getAvatar();

// after
playerEntityId =
    getState(ConnectionState.class).getService(GameSessionClientService.class).getPlayer();
```

The deprecated `getAvatar()` is kept for one cycle (logs a one-time warn when called) and forwards to a lazy `CurrentShip` lookup; downstream consumers migrate to the new identity in the same slice.

`ModelViewState` (canonical example):

```java
// In tryInitializeAvatar()
playerEntity = ed.watchEntity(playerId, Name.class, CurrentShip.class);

// In update()
if (playerEntity.applyChanges()) {
  final CurrentShip current = playerEntity.get(CurrentShip.class);
  final EntityId newShipId = current == null ? null : current.shipId();
  if (!Objects.equals(currentShipId, newShipId)) {
    if (shipEntity != null) shipEntity.release();
    shipEntity = newShipId == null ? null : ed.watchEntity(newShipId, Frequency.class);
    currentShipId = newShipId;
  }
}
if (shipEntity != null && shipEntity.applyChanges()) {
  final Frequency f = shipEntity.get(Frequency.class);
  if (f != null) {
    avatarFrequency = f.getFrequency();
    updateFlagMaterials(avatarFrequency);
  }
}
```

Ghost-state semantics:
- `currentShipId == null` → no ship-specific HUD (no flag materials update, no camera-track target, no movement input target).
- The transition is reactive — when respawn lands and `CurrentShip` is restamped, the watcher rebinds automatically.

`InfinityCameraState`, `AvatarMovementState`, `RadarState`, `PositionHudState`, `JitterState`, `HudLabelState`, `PlayerListState` all follow the same template: watch the player, lazy-resolve current ship, hide ship-specific UI when ghost.

### P5 — `Player` → `PlayerShip` rename

The current `infinity.es.ship.Player` marker is on the ship, not the player. The new component is named `PlayerShip` to reflect what it actually marks. Mechanical rename:

- File move: `api/src/main/java/infinity/es/ship/Player.java` → `PlayerShip.java`
- Class rename + all imports + filter usages (`ChecksShipsSystem`, `ArenaSystem`, `PrizeConsumptionSystem`, `PrizeSpawnerSystem`, `PlayerListState`, `ModelViewState`, `GameServer.registerSerializers`).
- `ShipFactory.createPlayerShip` and `AIEntities.createMobShip` adjust the stamp/remove sites.

## Slice plan

Three independently-reviewable commits. P2 can land first as pure data-wiring; P4 then consumes it; P5 is a janitorial follow-up.

### Slice P2 — Add `CurrentShip` component + writer

- New `infinity.es.lifecycle.CurrentShip` component (api/).
- Register in `GameServer.registerSerializers()`.
- `AvatarSystem` stamps `CurrentShip(shipId)` on player at signon (existing `Captain` write site is the natural pair).
- `DeathSystem` (or `AvatarSystem` watching `Dead`) removes `CurrentShip` from the dying ship's parent player.
- Test: `AvatarSystemTest` covers stamp-on-signon + remove-on-death + restamp-on-respawn (when a respawn policy lands).
- No client read yet; existing client code untouched. F2.6 smoke regression: still NPEs on death, but with `CurrentShip` correctly absent on the player after death.

### Slice P4 — Client identity switch

- `GameSessionState.avatarEntityId` → `playerEntityId` (rename + read `getPlayer()`).
- Eight client states (`ModelViewState`, `RadarState`, `PositionHudState`, `JitterState`, `HudLabelState`, `PlayerListState`, `AvatarMovementState`, `InfinityCameraState`) migrate to the watch-player-resolve-ship pattern.
- `GameSession.getAvatar()` deprecated; emits a one-time warn.
- Test: launch arena, kill self, verify ghost state, verify no NPE.
- Acceptance: F2.6 smoke-test recipe (kill self in `test-modules`) produces ghost state, no NPE, ghost can spectate. Once `RespawnPolicyModule` lands (later F2 slice), the watcher rebinds to the new ship automatically.

### Slice P5 — `Player` → `PlayerShip` rename

- File move + class rename + import + filter updates.
- Acceptance: build clean, all tests pass, no behaviour change.

## Test strategy

- **Unit**: `AvatarSystemTest` (P2) covers the `CurrentShip` write/remove lifecycle on signon, death, respawn, ship-change.
- **Integration**: existing `ConfigRegistrySystemLoadTest` style for the spawn pipeline — no new framework.
- **Smoke**: F2.6 recipe + die-without-respawn = ghost state, no NPE. After `RespawnPolicyModule` lands, the same recipe = continuous loop with no NPE.

## Dependencies / consumers

**Upstream:**
- ADR-0001 (canonical writer rule — `AvatarSystem` owns `CurrentShip`).
- ADR-0005 (api/server/client layering — `CurrentShip` is api/, writers are server/, readers are client/).
- ADR-0008 (arena modules — future `RespawnPolicyModule` is one of the writers).

**Downstream:**
- F2's `InstantRespawn` (`RespawnPolicyModule`) — once it lands, it triggers ship recreation and `CurrentShip` restamp; the client picks it up reactively.
- `ScoreCoordinatorSystem` (later F2 slice) — scores are per-player, accumulated across deaths. The durable player entity is the natural home for `PlayerRoundScore` / `PlayerMatchScore` / `PlayerTotalScore`.
- [`account-system/`](../account-system/) — cross-session durable player identity. P2's `playerEntityId` is per-connection today; account-system extends to cross-session.

## Comments

(none yet)

## References

- [ADR-0001](../../docs/adr/0001-ecs-component-model.md) — canonical writer rule.
- [ADR-0005](../../docs/adr/0005-layered-architecture.md) — api/server/client layering.
- [ADR-0008](../../docs/adr/0008-arena-composition-and-modules.md) — arena modules (respawn policy is a module).
- [`arena-modules/PRD.md`](../arena-modules/PRD.md) — F2 slice queue (`InstantRespawn` consumer of `CurrentShip` lifecycle).
- [`account-system/`](../account-system/) — cross-session player identity (downstream).
