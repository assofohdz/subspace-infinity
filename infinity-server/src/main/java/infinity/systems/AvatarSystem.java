// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.event.EventBus;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.SimTime;
import com.simsilica.mathd.Vec3d;
import infinity.Ship;
import infinity.es.Captain;
import infinity.es.ChangeTarget;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.FrequencyChange;
import infinity.es.Parent;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.lifecycle.CurrentShip;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ResetLivePool;
import infinity.es.ship.ShipType;
import infinity.es.ship.ShipTypeChange;
import infinity.es.ship.actions.WarpToChange;
import infinity.events.arena.ShipEvent;
import infinity.modules.ArenaModuleSystem;
import infinity.modules.RosterModule;
import infinity.settings.EngineConfigSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages avatar lifecycle + drains {@link ShipTypeChange}; ship-type gating runs through the active {@link RosterModule}. */
public class AvatarSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(AvatarSystem.class);

  // Ship-type wire bytes (SPEC/WARBIRD/JAVELIN/SPIDER/LEVI/TERRIER/WEASEL/
  // LANCASTER/SHARK) live in api/ as `infinity.net.ShipTypeId`. The chat-side
  // entry point takes a raw byte off the wire — convert via
  // `ShipTypeId.fromWireId(byte)` for enum-level dispatch.
  // package-private for AvatarSystemTest — see syncCurrentShipLinks().
  EntityData ed;
  private EngineConfigSystem engineConfigSystem;
  private ArenaModuleSystem arenaModules;
  private EntitySet frequencies;
  private EntitySet shipTypeChanges;

  private EntitySet captains;
  /** Player ships entering the world — link target for {@link CurrentShip} stamp. */
  EntitySet playerShips;
  /** Player ships marked {@link Dead} — link source for {@link CurrentShip} removal. */
  EntitySet deadPlayerShips;

  public AvatarSystem() {
    // no-arg ctor — wiring happens in initialize()
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);
    arenaModules = requireSystem(ArenaModuleSystem.class);

    frequencies = ed.getEntities(ShapeInfo.class, Frequency.class);
    captains = ed.getEntities(ShapeInfo.class, Captain.class);
    shipTypeChanges = ed.getEntities(ShipTypeChange.class, ChangeTarget.class);
    playerShips = ed.getEntities(PlayerShip.class, Parent.class);
    deadPlayerShips = ed.getEntities(PlayerShip.class, Parent.class, Dead.class);
  }

  @Override
  protected void terminate() {

    frequencies.release();
    frequencies = null;

    captains.release();
    captains = null;

    shipTypeChanges.release();
    shipTypeChanges = null;

    playerShips.release();
    playerShips = null;

    deadPlayerShips.release();
    deadPlayerShips = null;
  }

  @SuppressWarnings("unused")
  @Override
  public void update(final SimTime tpf) {
    captains.applyChanges();
    shipTypeChanges.applyChanges();
    drainShipTypeChanges();
    syncCurrentShipLinks();
  }

  /**
   * Maintains {@link CurrentShip} on player entities per ADR-0008 / player-vs-ship-identity PRD.
   * Stamp on each newly-arrived player ship; in-place ship-change (same entity id, re-projected
   * via {@code ShipSpawnSystem}) does not fire an addedEntity event so the link stays valid
   * without extra work. Remove when the ship is marked {@link Dead} — the player becomes a
   * ghost until a respawn module rebinds.
   */
  // package-private for AvatarSystemCurrentShipTest.
  void syncCurrentShipLinks() {
    playerShips.applyChanges();
    deadPlayerShips.applyChanges();
    for (final Entity added : playerShips.getAddedEntities()) {
      final EntityId player = added.get(Parent.class).getParentEntityId();
      if (player == null) {
        continue;
      }
      ed.setComponent(player, new CurrentShip(added.getId()));
    }
    for (final Entity dead : deadPlayerShips.getAddedEntities()) {
      final EntityId player = dead.get(Parent.class).getParentEntityId();
      if (player == null) {
        continue;
      }
      final CurrentShip current = ed.getComponent(player, CurrentShip.class);
      if (current != null && dead.getId().equals(current.getShipId())) {
        ed.removeComponent(player, CurrentShip.class);
      }
    }
  }

  /** Canonical drain — value-replacement; last-write-wins same-tick. */
  private void drainShipTypeChanges() {
    final Map<EntityId, Ship> typeByShip = new LinkedHashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : shipTypeChanges.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final Ship newType = added.get(ShipTypeChange.class).newShipType();
      oneShotHolders.add(added.getId());
      if (ct == null || ct.target() == null || newType == null) {
        continue;
      }
      typeByShip.put(ct.target(), newType);
    }
    for (final Map.Entry<EntityId, Ship> e : typeByShip.entrySet()) {
      // Atomic stamp: setComponents writes ShipType + ResetLivePool in the same flush so the
      // ShipSpawnSystem (ShipType, ArenaId) watcher sees the new type AND the respawn marker
      // together. Without atomicity the watcher might project the OLD type's stats and then a
      // second projection would have to undo it.
      ed.setComponents(e.getKey(), new ShipType(e.getValue()), new ResetLivePool());
    }
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }
  }

  @Override
  public void start() {
    // no-op: lifecycle hook unused; entity wiring happens in initialize()
  }

  @Override
  public void stop() {
    // no-op: lifecycle hook unused; cleanup happens in terminate()
  }

  public void requestShipChange(final EntityId shipEntity, final byte shipType) {
    // ships.groovy live-reload is handled by ArenaSystem's per-arena file watcher.
    // Subspace canon EnterShipEnergy=100% — Infinity divergence: enforce full energy
    // (current Energy ≥ EnergyStats.max) before allowing a ship swap. No EnterShipEnergy
    // key in REFERENCE.md; the full-energy gate is the Infinity rule.
    final int freq = readFrequency(shipEntity);
    if (!hasFullEnergy(shipEntity) || !rosterAllows(shipEntity, shipType)) {
      return;
    }

    final String shapeName = shapeNameForShipType(shipType);
    if (shapeName != null) {
      ed.setComponent(
          shipEntity,
          ShapeInfo.create(shapeName, engineConfigSystem.get().shipRadius(), ed));
    }

    // ShipType + ResetLivePool atomicity: the canonical writer (drainShipTypeChanges below)
    // stamps both via setComponents in the same flush.
    final EntityId h = ed.createEntity();
    ed.setComponents(
        h,
        ChangeTarget.self(shipEntity),
        new ShipTypeChange(Ship.getShip(shipType)));

    emitWarpToArenaSpawn(shipEntity, freq);
    EventBus.publish(ShipEvent.shipSpawned, new ShipEvent(shipEntity));
  }

  private int readFrequency(final EntityId shipEntity) {
    final Frequency f = ed.getComponent(shipEntity, Frequency.class);
    return f == null ? 0 : f.getFrequency();
  }

  /**
   * Delegate to the ship's arena's active {@link RosterModule}. Arenas with no roster
   * module loaded refuse every change — surface a warn so the operator notices a
   * missing {@code roster} statement in the arena's {@code arena.groovy}.
   */
  private boolean rosterAllows(final EntityId shipEntity, final byte shipType) {
    final ArenaId arenaId = ed.getComponent(shipEntity, ArenaId.class);
    final Optional<RosterModule> roster =
        arenaId == null ? Optional.empty() : rosterFor(arenaId);
    if (roster.isEmpty()) {
      log.warn(
          "Ship {} requesting type {} but arena {} has no roster module loaded; refusing",
          shipEntity,
          shipType,
          arenaId);
      return false;
    }
    return roster.get().isShipAllowed(Ship.getShip(shipType));
  }

  private Optional<RosterModule> rosterFor(final ArenaId arenaId) {
    final ArenaModuleSystem.LoadedArena entry = arenaModules.loadedFor(arenaId);
    return entry == null ? Optional.empty() : entry.set().roster();
  }

  /** Teleport to the entity's current arena's configured spawn point; no-op if no {@link ArenaId} or arena has no spawn. */
  private void emitWarpToArenaSpawn(final EntityId shipEntity, final int freq) {
    final ArenaId arena = ed.getComponent(shipEntity, ArenaId.class);
    if (arena == null) {
      return;
    }
    final Vec3d target = getSystem(ArenaSystem.class).getArenaSpawn(arena.getArena(), freq);
    if (target == null) {
      return;
    }
    final EntityId warpHolder = ed.createEntity();
    ed.setComponents(warpHolder, ChangeTarget.self(shipEntity), new WarpToChange(target));
  }

  /** Subspace canon: full energy (current ≥ {@link EnergyStats#max()}) to switch ships. A ship with no {@link EnergyStats} (e.g. SPEC) is treated as eligible. */
  private boolean hasFullEnergy(final EntityId shipEntity) {
    final EnergyStats stats = ed.getComponent(shipEntity, EnergyStats.class);
    if (stats == null) {
      return true;
    }
    final Energy energy = ed.getComponent(shipEntity, Energy.class);
    final int current = energy == null ? 0 : energy.getEnergy();
    return current >= stats.max();
  }

  /**
   * Map a {@code shipType} byte (constants on this class) to the canonical
   * {@code ShapeNames} string. Returns {@code null} for unknown / SPEC types,
   * matching the prior switch's {@code default: break} behaviour.
   */
  @javax.annotation.Nullable
  private static String shapeNameForShipType(final byte shipType) {
    switch (shipType) {
      case 1:
        return ShapeNames.SHIP_WARBIRD;
      case 2:
        return ShapeNames.SHIP_JAVELIN;
      case 3:
        return ShapeNames.SHIP_SPIDER;
      case 4:
        return ShapeNames.SHIP_LEVI;
      case 5:
        return ShapeNames.SHIP_TERRIER;
      case 6:
        return ShapeNames.SHIP_WEASEL;
      case 7:
        return ShapeNames.SHIP_LANCASTER;
      case 8:
        return ShapeNames.SHIP_SHARK;
      default:
        return null;
    }
  }

  /**
   * Find the frequency of an entity.
   *
   * @param entityId the entity to check
   * @return the frequency of the entity
   */
  public int getFrequency(final EntityId entityId) {
    final Frequency freq = ed.getComponent(entityId, Frequency.class);

    return freq.getFrequency();
  }

  /**
   * Requests a frequency change for an entity. F2.5 leaves this ungated; F2.8 reroutes
   * through {@code FrequencyChangeRequestEvent} so the active {@code TeamSetupModule}
   * can apply per-team policy.
   */
  public void requestFreqChange(final EntityId entityId, final int newFreq) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(entityId), new FrequencyChange(newFreq));
  }

  /**
   * Sends every member of {@code team} back to freq 0 via the canonical drain.
   *
   * @param team the team to clear and reset
   */
  public void reset(final int team) {
    // Emit FrequencyChange(0) for every entity currently on `team` — drains via
    // the canonical FrequencySystem writer next tick.
    final ComponentFilter<Frequency> filter =
        FieldFilter.create(Frequency.class, "freq", Integer.valueOf(team));
    final EntitySet members = ed.getEntities(filter, Frequency.class);
    try {
      members.applyChanges();
      for (final Entity e : members) {
        final EntityId h = ed.createEntity();
        ed.setComponents(h, ChangeTarget.self(e.getId()), new FrequencyChange(0));
      }
    } finally {
      members.release();
    }
  }

  /**
   * Removes a player from this team. The removed player will be put in team 0.
   *
   * @param entityId the player entity to remove
   */
  public void removePlayer(final EntityId entityId) {
    // Could perhaps be that we should set frequency to 0 instead of removing
    // frequency
    if (entityId != null) {
      final EntityId h = ed.createEntity();
      ed.setComponents(h, ChangeTarget.self(entityId), new FrequencyChange(0));
    }
  }

  /**
   * Demotes a specified player from being a team captain.
   *
   * @param entityId the player to demote
   */
  public void removeCaptainFromTeam(final EntityId entityId) {
    ed.removeComponent(entityId, Captain.class);
  }

  /**
   * Determines whether a player is a team captain or not.
   *
   * @param entityId the entity to check
   * @return true if the player is a team captain, false otherwise
   */
  public boolean isCaptain(final EntityId entityId) {
    return captains.containsId(entityId);
  }

  /**
   * Makes a specified player a captain of this team.
   *
   * @param entityId the EntityId of the player to be promoted
   */
  public void addCaptain(final EntityId entityId) {
    ed.setComponent(entityId, new Captain());
  }

  /**
   * Gets the number of players on this team in a particular ship.
   *
   * @param team the frequency to check
   * @param type the type of ship
   * @return the count of the ship type
   */
  public int getShipCount(final int team, final ShapeInfo type) {

    final ComponentFilter<Frequency> freqFilter =
        FieldFilter.create(Frequency.class, "freq", Integer.valueOf(team));
    // try/finally so the short-lived EntitySet is always released — see
    // .claude/rules/entity-sets.md (arch-review TD-1).
    final EntitySet freq = ed.getEntities(freqFilter, Frequency.class, ShapeInfo.class);
    try {
      // Sum up the entities with the right type
      return freq.stream()
          .filter(e -> e.get(ShapeInfo.class).getShapeName(ed).equals(type.getShapeName(ed)))
          .map(_item -> Integer.valueOf(1))
          .reduce(Integer.valueOf(0), Integer::sum)
          .intValue();
    } finally {
      freq.release();
    }
  }
}
