// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.GameSystemManager;
import infinity.es.Dead;
import org.junit.Test;

/**
 * Pins {@link DeathSystem}'s Dead → Decay conversion. Without DeathSystem
 * registered, dead entities accumulate forever (Dead is stamped, no Decay,
 * SiO2 reaper never sees them, client wire bloats, EntityUpdater drops frames).
 */
public final class DeathSystemTest {

  @Test
  public void deadEntity_getsDecayStamped_andDeadCleared() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(DeathSystem.class, new DeathSystem());
    systems.initialize();
    systems.start();
    try {
      final EntityId victim = ed.createEntity();
      ed.setComponent(victim, new Dead(0L));

      systems.update();

      assertNotNull("DeathSystem must stamp Decay so the SiO2 reaper can despawn",
          ed.getComponent(victim, Decay.class));
      assertNull("DeathSystem clears Dead so the conversion is idempotent",
          ed.getComponent(victim, Dead.class));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
