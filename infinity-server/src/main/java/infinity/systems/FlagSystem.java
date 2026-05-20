// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.StaticBody;
import com.simsilica.sim.SimTime;
import infinity.es.Flag;
import infinity.es.FlagOwnership;
import infinity.es.Frequency;
import infinity.sim.GameSounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical writer of {@link FlagOwnership} on flag entities. Subscribes to
 * {@link ContactSystem} via {@link ContactListener} (registered in
 * {@link #initialize}); for each ship-vs-flag contact, writes the toucher's
 * {@link Frequency} onto the flag as its new owner. Replaces the legacy
 * {@code FrequencySystem} flag-touch handler — flag entities no longer carry
 * {@code Frequency}; owning team lives on {@link FlagOwnership}.
 */
public final class FlagSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  private static final Logger log = LoggerFactory.getLogger(FlagSystem.class);

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> phys;
  private EntitySet shipFrequencies;
  private EntitySet flags;
  private SimTime time;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    phys = requireSystem(PhysicsSpace.class);
    shipFrequencies = ed.getEntities(Frequency.class);
    flags = ed.getEntities(Flag.class);
    requireSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {
    requireSystem(ContactSystem.class).removeListener(this);
    shipFrequencies.release();
    shipFrequencies = null;
    flags.release();
    flags = null;
  }

  @Override
  public void update(final SimTime tpf) {
    shipFrequencies.applyChanges();
    flags.applyChanges();
    time = tpf;
  }

  @Override
  public void newContact(final Contact contact) {
    final RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    final AbstractBody<EntityId, MBlockShape> body2 = contact.body2;
    if (!(body2 instanceof StaticBody)) {
      return;
    }
    final EntityId ship = body1.id;
    final EntityId flag = body2.id;
    if (!shipFrequencies.containsId(ship) || !flags.containsId(flag)) {
      return;
    }
    final int shipFreq = shipFrequencies.getEntity(ship).get(Frequency.class).getFrequency();
    final FlagOwnership current = ed.getComponent(flag, FlagOwnership.class);
    if (current != null && current.freq() == shipFreq) {
      // No-op; RaM rule #6.
      contact.disable();
      return;
    }
    ed.setComponent(flag, new FlagOwnership(shipFreq));
    if (time != null) {
      GameSounds.createFlagSound(ed, EntityId.NULL_ID, phys, time.getTime(), body2.position);
    }
    if (log.isDebugEnabled()) {
      log.debug("Flag {} claimed by ship {} (freq={})", flag, ship, shipFreq);
    }
    contact.disable();
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
