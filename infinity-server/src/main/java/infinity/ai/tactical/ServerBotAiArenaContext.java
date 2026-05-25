// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.capability.ArenaCapabilityNorms;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.field.NavigationFields;
import infinity.ai.field.ScalarField;
import infinity.ai.field.TileScored;
import infinity.ai.field.combat.CombatDensityField;
import infinity.ai.field.density.ArenaDensity;
import infinity.ai.field.opportunity.OpportunityField;
import infinity.ai.field.threat.ArenaThreat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Per-arena {@link BotAiArenaContext} the brain reads through the blackboard (ADR-0012). The
 * spatial fields (navigation, density, threat, opportunity, combat) are {@code null} until the
 * arena's map (hence its passability grid) has loaded; norms + synergy are always present.
 * {@code originCell*} is the arena's world-min corner, used to convert a bot's absolute world cell
 * into the arena-relative cell every field grid is indexed by. {@code chokepointPool} is the
 * load-time geometric candidate list (sorted by pinch); {@link #chokepoints()} re-ranks it live by
 * the traffic-heatmap combo.
 */
public record ServerBotAiArenaContext(
    ArenaCapabilityNorms norms,
    BotSynergyTable synergyTable,
    @Nullable NavigationFields navigation,
    int originCellX,
    int originCellZ,
    @Nullable ArenaDensity density,
    @Nullable ArenaThreat threatFields,
    @Nullable OpportunityField opportunityField,
    @Nullable CombatDensityField combat,
    List<TileScored> chokepointPool,
    double chokepointDensityWeight)
    implements BotAiArenaContext {

  @Override
  @Nullable
  public NavigationFields navigation() {
    return this.navigation;
  }

  @Override
  public ScalarField teamDensity(final int freq) {
    return this.density == null ? ScalarField.EMPTY : this.density.teamDensity(freq);
  }

  @Override
  public Set<Integer> activeTeamFreqs() {
    return this.density == null ? Set.of() : this.density.freqs();
  }

  @Override
  public ScalarField threat(final int freq) {
    return this.threatFields == null ? ScalarField.EMPTY : this.threatFields.threat(freq);
  }

  @Override
  public ScalarField opportunity() {
    return this.opportunityField == null ? ScalarField.EMPTY : this.opportunityField;
  }

  @Override
  public List<TileScored> chokepoints() {
    if (this.chokepointPool.isEmpty()) {
      return List.of();
    }
    final double k = this.chokepointDensityWeight;
    final List<TileScored> ranked = new ArrayList<>(this.chokepointPool.size());
    for (final TileScored c : this.chokepointPool) {
      final double combatV = this.combat == null ? 0.0 : this.combat.valueAt(c.x(), c.y());
      final double densV = this.density == null ? 0.0 : this.density.totalAt(c.x(), c.y());
      ranked.add(new TileScored(c.x(), c.y(), c.score() * (1.0 + combatV + k * densV)));
    }
    ranked.sort(Comparator.comparingDouble(TileScored::score).reversed());
    return ranked;
  }
}
