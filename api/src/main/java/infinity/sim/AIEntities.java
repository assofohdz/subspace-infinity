// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.config.EngineConfig;
import infinity.es.BotBrain;
import infinity.es.Frequency;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.sim.specs.ShipArgs;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AIEntities {

  /**
   * Sample names for AI mobs. Picked uniformly at random at spawn time so server logs +
   * future HUD can render readable identities ("Razor killed Echo") instead of raw
   * entity ids. List can grow without coordination with other code.
   */
  private static final List<String> BOT_NAMES = List.of(
      "Vex", "Drak", "Nyx", "Zorn", "Kael", "Echo", "Shade", "Wraith",
      "Razor", "Spike", "Talon", "Fang", "Viper", "Cobra", "Falcon", "Raven",
      "Crusher", "Reaper", "Slayer", "Smasher");

  private AIEntities() {
    // no instances
  }

  public static EntityId createMobShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final byte ship) {

    final EntityId mob =
        ShipFactory.createShip(
            ed,
            new ShipArgs(
                spawnLoc, owner, phys, createdTime, ship, EngineConfig.DEFAULTS.shipRadius()));
    ed.setComponent(mob, new MovementInput(new Vec3d(), new Quatd(), MovementInput.NONE));
    ed.setComponent(mob, new Name(randomBotName()));
    ed.setComponent(mob, new Frequency(1));
    // Positive marker for AI-driven ships — replaces the older "bot = absence of
    // PlayerShip" inverse pattern. createShip never stamps PlayerShip, so no remove
    // call is needed.
    ed.setComponent(mob, new BotShip());
    // BotBrainSystem owns this entity's MovementInput from here on; v1 is a marker only.
    ed.setComponent(mob, new BotBrain());

    return mob;
  }

  private static String randomBotName() {
    return BOT_NAMES.get(ThreadLocalRandom.current().nextInt(BOT_NAMES.size()));
  }
}
