// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.Frequency;
import infinity.es.FrequencyChange;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandTriFunction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical writer for {@link Frequency} on ship/player entities; drains {@link FrequencyChange}
 * (ADR 0001). Emit path: {@code =N} chat command (player retargets own frequency). The
 * legacy ship-vs-flag contact handler moved to {@code FlagSystem}, which writes
 * {@code FlagOwnership} on flag entities — flags no longer carry {@code Frequency}.
 */
public class FrequencySystem extends BaseInfinitySystem {

  private final Pattern freuencyChange = Pattern.compile("=(\\d+)");
  private EntityData ed;
  private EntitySet frequencyChanges;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    frequencyChanges = ed.getEntities(FrequencyChange.class, ChangeTarget.class);

    final InfinityChatHostedService chat = getSystem(InfinityChatHostedService.class);
    chat.registerPatternTriConsumer(
        freuencyChange,
        "The command to load a new map is ~loadArena <mapName>, where <mapName> is the "
            + "name of the map you want to load",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::changeFrequency));
  }

  /**
   * Changes the frequency of the player's avatar.
   *
   * @param entityId The id of the player
   * @param m The matcher that contains the frequency as group 1
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String changeFrequency(
      final EntityId entityId, final EntityId avatarEntityId, final Matcher m) {
    final EntityId h = ed.createEntity();
    ed.setComponents(
        h,
        ChangeTarget.self(avatarEntityId),
        new FrequencyChange(Integer.parseInt(m.group(1))));
    return "Frequency changed to " + m.group(1);
  }

  @Override
  protected void terminate() {
    frequencyChanges.release();
    frequencyChanges = null;
  }

  @Override
  public void update(final SimTime time) {
    frequencyChanges.applyChanges();
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
