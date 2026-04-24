/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.systems;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.event.EventBus;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import com.simsilica.mathd.Vec3d;
import infinity.InfinityConstants;
import infinity.Ship;
import infinity.ShipRestrictor;
import infinity.es.Captain;
import infinity.es.Frequency;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.ship.ShipType;
import infinity.es.ship.actions.WarpTo;
import infinity.events.ShipEvent;
import infinity.settings.GroovyShipLoader;
import infinity.sim.CorePhysicsConstants;
import java.util.HashMap;

/**
 * This system is responsible for managing the avatar entities. It is responsible
 * for creating and destroying them as players join and leave the game. It also
 * manages the team restrictions for each ship.
 *
 * @author Asser
 */
public class AvatarSystem extends AbstractGameSystem {

  public static final byte SPEC = 0x0;
  public static final byte WARBIRD = 0x1;
  public static final byte JAVELIN = 0x2;
  public static final byte SPIDER = 0x3;
  public static final byte LEVI = 0x4;
  public static final byte TERRIER = 0x5;
  public static final byte LANCASTER = 0x6;
  public static final byte WEASEL = 0x7;
  public static final byte SHARK = 0x8;
  private EntityData ed;
  private EntitySet frequencies;
  private EntitySet arenaEntities;
  private GroovyShipLoader shipLoader;
  /** The number of allowed players in each ship on this team. */
  private HashMap<Integer, ShipRestrictor> teamRestrictions;

  private EntitySet captains;

  public AvatarSystem() {
    // Nothing to do here
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);

    frequencies = ed.getEntities(ShapeInfo.class, Frequency.class);
    captains = ed.getEntities(ShapeInfo.class, Captain.class);
    arenaEntities = ed.getEntities(ArenaId.class);
    shipLoader = getSystem(GroovyShipLoader.class);

    teamRestrictions = new HashMap<>();
  }

  @Override
  protected void terminate() {

    frequencies.release();
    frequencies = null;

    arenaEntities.release();
    arenaEntities = null;

    captains.release();
    captains = null;
  }

  @SuppressWarnings("unused")
  @Override
  public void update(final SimTime tpf) {

    if (captains.applyChanges()) {
      for (final Entity e : captains.getAddedEntities()) {
        // TODO implement me
      }
      for (final Entity e : captains.getChangedEntities()) {
        // TODO implement me
      }
      for (final Entity e : captains.getRemovedEntities()) {
        // TODO implement me
      }
    }
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

    // Hot-reload the arena's ships.groovy so dev edits take effect on the next ship change.
    // Fast: filesystem read + Groovy parse, microseconds. Resolved ambiently (Option R2).
    arenaEntities.applyChanges();
    final java.util.Iterator<com.simsilica.es.Entity> arenaIter = arenaEntities.iterator();
    final ArenaId currentArena = arenaIter.hasNext() ? arenaIter.next().get(ArenaId.class) : null;
    if (currentArena != null) {
      final SettingsSystem settings = getSystem(SettingsSystem.class);
      final String scriptPath =
          settings.getString(currentArena.getArena(), "Scripts", "Ships", null);
      shipLoader.apply(currentArena, scriptPath);
    }

    // Frequency-based ship restrictions are optional — human player ships don't carry
    // a Frequency component today (only freq-aware spawn paths add it). Read directly
    // via EntityData and treat missing as "no frequency known" → skip the restrictor
    // check so basic ship change works end-to-end.
    final Frequency freqComponent = ed.getComponent(shipEntity, Frequency.class);
    final int freq = (freqComponent != null) ? freqComponent.getFrequency() : 0;
    final ShipRestrictor restrictor = (freqComponent != null) ? getRestrictor(freq) : null;

    // Allow ship change if no restrictions on frequency, or if restrictions allow it.
    if (restrictor == null || restrictor.canSwitch(shipEntity, shipType, freq)) {

      switch (shipType) {
        case 1:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_WARBIRD, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 2:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_JAVELIN, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 3:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_SPIDER, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 4:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_LEVI, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 5:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_TERRIER, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 6:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_WEASEL, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 7:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_LANCASTER, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        case 8:
          ed.setComponent(
              shipEntity,
              ShapeInfo.create(ShapeNames.SHIP_SHARK, CorePhysicsConstants.SHIPSIZERADIUS, ed));
          break;
        default:
          break;
      }

      // Re-project ship stats from the arena's ShipConfig (Pattern 4). Remove+set forces
      // an add event on the ShipType EntitySet so ShipSpawnSystem re-runs even if the
      // ship type is unchanged — useful for the dev loop where the same key is pressed
      // after editing ships.groovy to re-apply tuning without a full restart.
      ed.removeComponent(shipEntity, ShipType.class);
      ed.setComponent(shipEntity, new ShipType(Ship.getShip(shipType)));

      // Teleport to the arena's configured spawn point. Reads [Spawn] X / Z from the
      // arena's settings; falls back to (0, 0) if either is absent. Ambient arena
      // resolution (Option R2 — TODO in GameEntities.createShip): first loaded arena wins.
      // WarpSystem picks up the WarpTo component and handles the actual body teleport.
      arenaEntities.applyChanges();
      if (!arenaEntities.isEmpty()) {
        final ArenaId arena = arenaEntities.iterator().next().get(ArenaId.class);
        final SettingsSystem settings = getSystem(SettingsSystem.class);
        final int spawnX = settings.getInt(arena.getArena(), "Spawn", "X", 0);
        final int spawnZ = settings.getInt(arena.getArena(), "Spawn", "Z", 0);
        final Vec3d target = new Vec3d(spawnX, InfinityConstants.GAMEPLAY_Y, spawnZ);
        ed.setComponent(shipEntity, new WarpTo(target));
      }

      EventBus.publish(ShipEvent.shipSpawned, new ShipEvent(shipEntity));
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
    final EntitySet freq = ed.getEntities(freqFilter, Frequency.class, ShapeInfo.class);

    int count = 0;

    // Sum up the entities with the right type
    count =
        freq.stream()
            .filter(e -> (e.get(ShapeInfo.class).getShapeName(ed).equals(type.getShapeName(ed))))
            .map(_item -> Integer.valueOf(1))
            .reduce(Integer.valueOf(count), Integer::sum)
            .intValue();

    return count;
  }
}
