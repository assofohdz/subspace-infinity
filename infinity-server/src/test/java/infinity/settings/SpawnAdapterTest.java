// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import infinity.config.SpawnConfig;
import infinity.config.TeamSpawn;
import org.junit.Test;

/** Pins {@link SpawnAdapter} DSL → {@link SpawnConfig} translation; see REFERENCE.md {@code ## Spawn}. */
public class SpawnAdapterTest {

  @Test
  public void evaluate_teamEntries_capturedInDeclarationOrder() {
    // Subspace canon: Team0/1/2/3-X/Y/Radius. n-th team {…} call binds to
    // freq n; freq > teams.size() wraps via floorMod (handled by SpawnConfig).
    // y is Subspace vertical = Infinity Z (TeamSpawn.y javadoc).
    final String src =
        "spawn {\n"
            + "  team x: 416, y: -480, radius: 96\n"
            + "  team x: -416, y: 480, radius: 96\n"
            + "}\n";

    final SpawnConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(SpawnAdapter.INSTANCE, src, "test:spawn_teams");

    assertEquals(2, cfg.teams().size());
    final TeamSpawn t0 = cfg.teams().get(0);
    assertEquals(416, t0.x());
    assertEquals(-480, t0.y());
    assertEquals(96, t0.radiusTiles());
    final TeamSpawn t1 = cfg.teams().get(1);
    assertEquals(-416, t1.x());
    assertEquals(480, t1.y());
  }

  @Test
  public void evaluate_emptyBlock_yieldsEmptyTeamsAndZeroRadius() {
    final SpawnConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(SpawnAdapter.INSTANCE, "spawn { }\n", "test:spawn_empty");

    assertTrue("empty teams signals fallback to legacy ArenaConfig.spawnX/spawnZ", cfg.teams().isEmpty());
    assertEquals(0, cfg.spawnRadius());
  }

  @Test
  public void spawnRadius_capturedAsTiles_divergesFromWarpRadiusLimit() {
    // Infinity divergence: REFERENCE.md §Misc WarpRadiusLimit anchors on
    // arena center with "1024 = anywhere" sentinel. Infinity's spawnRadius
    // anchors on arena.groovy-declared spawn coord; 0 = exact-point.
    final String src = "spawn { spawnRadius 50 }\n";

    final SpawnConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(SpawnAdapter.INSTANCE, src, "test:spawn_radius");

    assertEquals(50, cfg.spawnRadius());
    assertTrue(cfg.teams().isEmpty());
  }

  @Test
  public void team_missingNumericArg_rejected_returnsDefaults() {
    final String src = "spawn { team x: 1, y: 'not-a-number', radius: 3 }\n";

    final SpawnConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(SpawnAdapter.INSTANCE, src, "test:spawn_badArg");

    assertSame(SpawnConfig.DEFAULTS, cfg);
  }

  @Test
  public void forFreq_emptyTeams_returnsNullForLegacyFallback() {
    final SpawnConfig empty = SpawnConfig.DEFAULTS;
    assertNull("empty teams → null sentinel, consumer falls back to ArenaConfig.spawnX/spawnZ",
        empty.forFreq(0));
  }

  @Test
  public void forFreq_modWrap_assignsFreqsAcrossTeams() {
    final String src =
        "spawn {\n"
            + "  team x: 1, y: 1, radius: 1\n"
            + "  team x: 2, y: 2, radius: 2\n"
            + "}\n";

    final SpawnConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(SpawnAdapter.INSTANCE, src, "test:spawn_modWrap");

    assertNotNull(cfg.forFreq(0));
    assertEquals(1, cfg.forFreq(0).x());
    assertEquals(2, cfg.forFreq(1).x());
    // Freq 2 wraps back to Team0 (mod 2).
    assertEquals(1, cfg.forFreq(2).x());
    assertEquals(2, cfg.forFreq(3).x());
  }
}
