// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import infinity.Ship;
import infinity.config.ShipConfig;
// Same package — ShipConfigBuilder resolves without an explicit import after
// the arch-review #10 extraction (was nested in GroovyShipLoader; now a
// top-level package-private class in infinity.settings).
import org.junit.Test;

/**
 * Verifies that {@code radarRange} flows through the Groovy ship-config builder
 * onto {@link ShipConfig#radarRange()}, and that omitting it falls back to the
 * documented default. Uses {@link ShipConfigBuilder} directly (package-private
 * constructor) instead of standing up a full {@code GroovyShell} pipeline.
 */
public class GroovyShipLoaderRadarTest {

  private static final double EPSILON = 1e-9;

  @Test
  public void radarRange_setExplicitly_propagatesToShipConfig() {
    final ShipConfigBuilder builder = new ShipConfigBuilder(Ship.WARBIRD);
    builder.radarRange(Integer.valueOf(400));

    final ShipConfig cfg = builder.build();

    assertEquals(400.0, cfg.radarRange(), EPSILON);
    assertEquals(Ship.WARBIRD, cfg.type());
  }

  @Test
  public void radarRange_omitted_defaultsToDocumentedValue() {
    final ShipConfigBuilder builder = new ShipConfigBuilder(Ship.JAVELIN);

    final ShipConfig cfg = builder.build();

    assertEquals(GroovyShipLoader.DEFAULT_RADAR_RANGE, cfg.radarRange(), EPSILON);
  }

  @Test
  public void fallbackRegistry_carriesDefaultRadarRange() {
    // The static FALLBACK snapshot is what every arena gets if its ships.groovy
    // is missing or fails to evaluate. radarRange must round-trip through
    // fallbackShip() with the documented default so unconfigured arenas still
    // ship a working radar config.
    final ShipConfig fallback = GroovyShipLoader.FALLBACK.getShip(Ship.WARBIRD);

    assertNotNull("FALLBACK must contain WARBIRD", fallback);
    assertEquals(GroovyShipLoader.DEFAULT_RADAR_RANGE, fallback.radarRange(), EPSILON);
  }
}
