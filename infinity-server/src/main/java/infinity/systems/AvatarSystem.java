// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.event.EventBus;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.SimTime;
import com.simsilica.mathd.Vec3d;
import infinity.Ship;
import infinity.sim.ShipRestrictor;
import infinity.es.Captain;
import infinity.es.Frequency;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.ship.ResetLivePool;
import infinity.es.ship.ShipType;
import infinity.es.ship.actions.WarpTo;
import infinity.events.arena.ShipEvent;
import infinity.settings.EngineConfigSystem;
import java.util.HashMap;
import java.util.Map;

/**
 * This system is responsible for managing the avatar entities. It is responsible
 * for creating and destroying them as players join and leave the game. It also
 * manages the team restrictions for each ship.
 *
 * @author Asser
 */
public class AvatarSystem extends BaseInfinitySystem {

  // Ship-type wire bytes (SPEC/WARBIRD/JAVELIN/SPIDER/LEVI/TERRIER/WEASEL/
  // LANCASTER/SHARK) now live in api/ as `infinity.net.ShipTypeId`. The
  // method below takes a raw byte off the wire — convert via
  // `ShipTypeId.fromWireId(byte)` if you need enum-level dispatch.
  private EntityData ed;
  private EngineConfigSystem engineConfigSystem;
  private EntitySet frequencies;
  /** The number of allowed players in each ship on this team. */
  private Map<Integer, ShipRestrictor> teamRestrictions;

  private EntitySet captains;

  public AvatarSystem() {
    // Nothing to do here
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);

    frequencies = ed.getEntities(ShapeInfo.class, Frequency.class);
    captains = ed.getEntities(ShapeInfo.class, Captain.class);

    teamRestrictions = new HashMap<>();
  }

  @Override
  protected void terminate() {

    frequencies.release();
    frequencies = null;

    captains.release();
    captains = null;
  }

  @SuppressWarnings("unused")
  @Override
  public void update(final SimTime tpf) {
    // Keep `captains` current — isCaptain() reads from the live set.
    // TODO: react to add / change / remove events when captain logic lands.
    captains.applyChanges();
  }

  @Override
  public void start() {
    // Auto-generated method stub
  }

  @Override
  public void stop() {
    // Auto-generated method stub
  }

  /**
   * Request a ship change.
   *
   * @param shipEntity The entity to change
   * @param shipType The ship type to change to
   */
  public void requestShipChange(final EntityId shipEntity, final byte shipType) {
    // TODO: Check for energy (full energy to switch ships)

    // ships.groovy live-reload is handled by ArenaSystem's per-arena file watcher
    // (fires on save, not on key press). Nothing to do here on ship change.

    // Frequency-based ship restrictions are optional — human player ships don't carry
    // a Frequency component today (only freq-aware spawn paths add it). Read directly
    // via EntityData and treat missing as "no frequency known" → skip the restrictor
    // check so basic ship change works end-to-end.
    final Frequency freqComponent = ed.getComponent(shipEntity, Frequency.class);
    final int freq = (freqComponent != null) ? freqComponent.getFrequency() : 0;
    final ShipRestrictor restrictor = (freqComponent != null) ? getRestrictor(freq) : null;

    // Allow ship change if no restrictions on frequency, or if restrictions allow it.
    if (restrictor == null || restrictor.canSwitch(shipEntity, shipType, freq)) {

      final String shapeName = shapeNameForShipType(shipType);
      if (shapeName != null) {
        ed.setComponent(
            shipEntity,
            ShapeInfo.create(shapeName, engineConfigSystem.get().shipRadius(), ed));
      }

      // Re-project ship stats from the arena's ShipConfig (Pattern 4). Remove+set
      // surfaces in ShipSpawnSystem.update() — Zay-ES coalesces same-tick
      // remove+set on a tracked field into a "changed" event (not "added"), so we
      // also stamp ResetLivePool to flag this projection as a respawn (= reset
      // Health/Energy and re-stamp *CurrentLevel starts from ShipConfig). Without
      // the marker, swapping to a weapon-equipped ship from a non-equipped one
      // leaves the *CurrentLevel components never written and the weapon
      // EntitySet membership filter excludes the ship → no fire.
      ed.removeComponent(shipEntity, ShipType.class);
      ed.setComponent(shipEntity, new ShipType(Ship.getShip(shipType)));
      ed.setComponent(shipEntity, new ResetLivePool());

      // Teleport to the ship's *current* arena's configured spawn point.
      // ArenaId is maintained by ArenaMembershipSystem (sensor contacts) +
      // WarpSystem (post-teleport reconcile) + spawn-time seeding in
      // GameSessionHostedService / BasicEnvironment. A null ArenaId (or
      // unloaded arena) means there's no spawn coord to teleport to —
      // skip the warp and let the ship-change happen in place.
      // ArenaSystem.getArenaSpawn(arenaName, freq) reads Pattern 4 typed
      // SpawnConfig (per-team) when the arena has a spawn.groovy authored;
      // falls back to legacy ArenaConfig.spawnX/spawnZ otherwise.
      final ArenaId arena = ed.getComponent(shipEntity, ArenaId.class);
      if (arena != null) {
        final Vec3d target = getSystem(ArenaSystem.class).getArenaSpawn(arena.getArena(), freq);
        if (target != null) {
          ed.setComponent(shipEntity, new WarpTo(target));
        }
      }

      EventBus.publish(ShipEvent.shipSpawned, new ShipEvent(shipEntity));
    }
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
   * Requests a frequency change for an entity.
   *
   * @param entityId the entity to change frequency for
   * @param newFreq the new freuency
   */
  public void requestFreqChange(final EntityId entityId, final int newFreq) {
    // TODO: Check the ship restrictor in place to make sure the new frequency is
    // allowed
    ed.setComponent(entityId, new Frequency(newFreq));
  }

  /**
   * Sets the ShipRestrictor this team uses to restrict ship access. If restrictor is null, the team
   * will be set to use a Restrictor that allows full access to all ships.
   *
   * @param team Frequency
   * @param restrict The new ShipRestrictor to use
   */
  public void setRestrictor(final int team, final ShipRestrictor restrict) {
    if (!teamRestrictions.containsKey(Integer.valueOf(team))) {
      teamRestrictions.put(
          Integer.valueOf(team),
          new ShipRestrictor() {
            @Override
            public boolean canSwitch(final EntityId p, final byte ship, final int t) {
              return true;
            }

            @Override
            public boolean canSwap(final EntityId p1, final EntityId p2, final int t) {
              return true;
            }

            @Override
            public byte fallbackShip() {
              return 0;
            }
          });
    } else {
      teamRestrictions.put(Integer.valueOf(team), restrict);
    }
  }

  public ShipRestrictor getRestrictor(final int team) {
    return teamRestrictions.get(Integer.valueOf(team));
  }

  /**
   * Resets this team to completely empty, just as when it was instantiated This does not change the
   * ShipRestrictor, however.
   *
   * @param team the team to clear and reset
   */
  public void reset(final int team) {
    //When we get to this, we could do something like:
    //players.clear(); changed = true; plist = null; ships = new Player[8][0];
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
      final Frequency freq = new Frequency(0);
      ed.setComponent(entityId, freq);
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
    return (captains.containsId(entityId));
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
          .filter(e -> (e.get(ShapeInfo.class).getShapeName(ed).equals(type.getShapeName(ed))))
          .map(_item -> Integer.valueOf(1))
          .reduce(Integer.valueOf(0), Integer::sum)
          .intValue();
    } finally {
      freq.release();
    }
  }
}
