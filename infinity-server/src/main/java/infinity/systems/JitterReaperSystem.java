// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.Jitter;

/** Reaps expired {@link Jitter} components (endTime ≤ now). */
public class JitterReaperSystem extends AbstractGameSystem {

  private EntityData ed;
  private EntitySet jittering;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    jittering = ed.getEntities(Jitter.class);
  }

  @Override
  protected void terminate() {
    jittering.release();
    jittering = null;
  }

  @Override
  public void update(final SimTime tpf) {
    jittering.applyChanges();
    final long now = tpf.getTime();
    for (final Entity e : jittering) {
      if (now >= e.get(Jitter.class).getEndTime()) {
        ed.removeComponent(e.getId(), Jitter.class);
      }
    }
  }

  @Override
  public void start() {
    // intentionally empty
  }

  @Override
  public void stop() {
    // intentionally empty
  }
}
