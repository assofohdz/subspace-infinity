// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import infinity.ai.field.combat.CombatDensityField;
import infinity.ai.field.density.ArenaDensity;
import infinity.ai.field.nav.AsyncNavigationFields;
import infinity.ai.field.opportunity.OpportunityField;
import infinity.ai.field.threat.ArenaThreat;
import java.util.List;

/** Per-arena flow-field nav + spatial fields + the passability grid it was built from (rebuilt on grid swap). */
public record ArenaNav(
    boolean[][] grid,
    AsyncNavigationFields fields,
    ArenaDensity density,
    ArenaThreat threat,
    OpportunityField opportunity,
    CombatDensityField combat,
    List<TileScored> chokepointPool,
    int originX,
    int originZ,
    int width,
    int height) {}
