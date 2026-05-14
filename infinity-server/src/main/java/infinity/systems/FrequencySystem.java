// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.Entity;
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
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.Flag;
import infinity.es.Frequency;
import infinity.es.FrequencyChange;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameSounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical writer for {@link Frequency}; drains {@link FrequencyChange}
 * (ADR 0001). Two emit paths: ship-vs-flag contacts (claim the flag for the
 * toucher's frequency; source = toucher for attribution), and the
 * {@code =N} chat command (player retargets own frequency). See
 * {@code .claude/rules/replacement-as-mutation.md}.
 */
public class FrequencySystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  private final Pattern freuencyChange = Pattern.compile("=(\\d+)");
  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> phys;
  private EntitySet freqencies;
  private EntitySet flags;
  private EntitySet frequencyChanges;
  private SimTime time;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class, true);
    phys = getSystem(PhysicsSpace.class, true);

    freqencies = ed.getEntities(Frequency.class);
    flags = ed.getEntities(Flag.class);
    frequencyChanges = ed.getEntities(FrequencyChange.class, ChangeTarget.class);

    InfinityChatHostedService chat = getSystem(InfinityChatHostedService.class);
    // Register consuming methods for patterns
    chat.registerPatternTriConsumer(
        freuencyChange,
        "The command to load a new map is ~loadArena <mapName>, where <mapName> is the "
            + "name of the map you want to load",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::changeFrequency));

    // Register this as a contact listener with the ContactSystem
    getSystem(ContactSystem.class, true).addListener(this);
  }

  /**
   * Changes the frequency of the player's avatar.
   *
   * @param entityId The id of the player
   * @param m The matcher that contains the frequency as group 1
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String changeFrequency(final EntityId entityId, final EntityId avatarEntityId, final Matcher m) {
    final EntityId h = ed.createEntity();
    ed.setComponents(
        h,
        ChangeTarget.self(avatarEntityId),
        new FrequencyChange(Integer.parseInt(m.group(1))));
    return "Frequency changed to " + m.group(1);
  }

  @Override
  protected void terminate() {
    //Remove this as a contact listener with the ContactSystem
    getSystem(ContactSystem.class, true).removeListener(this);

    freqencies.release();
    freqencies = null;

    flags.release();
    flags = null;

    frequencyChanges.release();
    frequencyChanges = null;
  }

  @Override
  public void newContact(final Contact contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    // For now, all flags are static and cannot be picked up, but can change frequencies
    if (body2 instanceof StaticBody) {
      EntityId ship = body1.id;
      EntityId flag = body2.id;

      // Check if entity one is a ship and has a frequency and if entity two is flag with a
      // different frequency
      if (freqencies.containsId(ship) && flags.containsId(flag)) {
        int shipFreq = freqencies.getEntity(ship).get(Frequency.class).getFrequency();
        // Check if flag has a frequency
        if (freqencies.containsId(flag)) {
          int flagFreq = freqencies.getEntity(flag).get(Frequency.class).getFrequency();
          if (shipFreq != flagFreq) {
            // Claim the flag for the toucher's frequency via the canonical drain. Source = ship
            // so attribution points back at the player who flipped the flag.
            emitFrequencyChange(flag, ship, shipFreq);
            GameSounds.createFlagSound(ed, EntityId.NULL_ID, phys, time.getTime(), body2.position);
          }
        } else {
          // First-time claim of an unowned flag — same canonical-drain shape as above.
          emitFrequencyChange(flag, ship, shipFreq);
          GameSounds.createFlagSound(ed, EntityId.NULL_ID, phys, time.getTime(), body2.position);
        }
        contact.disable();
      }
    }
  }

  /** Emit a one-shot {@link FrequencyChange} holder targeting {@code target}, attributed to {@code source}. */
  private void emitFrequencyChange(
      final EntityId target, final EntityId source, final int newFrequency) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, new ChangeTarget(target, source), new FrequencyChange(newFrequency));
  }

  @Override
  public void update(final SimTime time) {
    freqencies.applyChanges();
    flags.applyChanges();
    frequencyChanges.applyChanges();

    this.time = time;
    drainFrequencyChanges();
  }

  /**
   * Canonical drain for {@link FrequencyChange} (ADR 0001). Value-replacement; one-shot only
   * (frequency changes don't reverse on Decay expiry). Same-tick fold is last-write-wins.
   */
  private void drainFrequencyChanges() {
    final Map<EntityId, Integer> freqByTarget = new LinkedHashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : frequencyChanges.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final int newFreq = added.get(FrequencyChange.class).newFrequency();
      oneShotHolders.add(added.getId());
      if (ct == null || ct.target() == null) {
        continue;
      }
      freqByTarget.put(ct.target(), Integer.valueOf(newFreq));
    }
    for (final Map.Entry<EntityId, Integer> e : freqByTarget.entrySet()) {
      applyFrequency(e.getKey(), e.getValue().intValue());
    }
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }
  }

  private void applyFrequency(final EntityId target, final int newFreq) {
    final Frequency current = ed.getComponent(target, Frequency.class);
    if (current != null && current.getFrequency() == newFreq) {
      // RaM rule #6 — skip no-op writes (no spurious "changed" event for HudLabelState/etc.).
      return;
    }
    ed.setComponent(target, new Frequency(newFreq));
  }
}
