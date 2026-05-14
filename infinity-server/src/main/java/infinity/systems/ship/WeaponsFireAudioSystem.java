// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.FireRequest;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.WeaponType;
import infinity.sim.GameSounds;
import infinity.systems.BaseInfinitySystem;

/** Drains {@link FireRequest} holders, spawns fire-sound entities via {@link GameSounds}; destroys the holder after both consumers run. See ADR 0001. */
public class WeaponsFireAudioSystem extends BaseInfinitySystem {

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;

  private EntitySet fireRequests;

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    fireRequests = ed.getEntities(FireRequest.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    fireRequests.release();
    fireRequests = null;
  }

  @Override
  public void update(final SimTime tpf) {
    fireRequests.applyChanges();
    final long now = tpf.getTime();
    for (final Entity holder : fireRequests) {
      final FireRequest req = holder.get(FireRequest.class);
      final ChangeTarget tgt = holder.get(ChangeTarget.class);
      final EntityId attacker = tgt.target();
      createSound(attacker, req, now);
      // One-shot: audio system is the last drain in the registration order, owns destruction.
      ed.removeEntity(holder.getId());
    }
  }

  private void createSound(final EntityId attacker, final FireRequest req, final long now) {
    switch (req.weaponType()) {
      case WeaponType.BULLET:
        final BulletCurrentLevel bullLevel = ed.getComponent(attacker, BulletCurrentLevel.class);
        GameSounds.createBulletSound(
            ed, attacker, physicsSpace, now, req.location(), bullLevel.getLevel());
        break;
      case WeaponType.BOMB:
        final BombCurrentLevel bombLevel = ed.getComponent(attacker, BombCurrentLevel.class);
        GameSounds.createBombSound(
            ed, attacker, physicsSpace, now, req.location(), bombLevel.getLevel());
        break;
      case WeaponType.GRAVBOMB:
        // Gravbomb audio inherits the ship's current bomb level (Subspace
        // canon: gravbombs are level-3 bombs).
        final BombCurrentLevel gravBombLevel = ed.getComponent(attacker, BombCurrentLevel.class);
        GameSounds.createBombSound(
            ed, attacker, physicsSpace, now, req.location(), gravBombLevel.getLevel());
        break;
      case WeaponType.MINE:
        final MineCurrentLevel mineLevel = ed.getComponent(attacker, MineCurrentLevel.class);
        GameSounds.createMineSound(
            ed, attacker, physicsSpace, now, req.location(), mineLevel.getLevel());
        break;
      case WeaponType.BURST:
        // No burst-fire sound today (parity with pre-split behaviour).
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + req.weaponType());
    }
  }
}
