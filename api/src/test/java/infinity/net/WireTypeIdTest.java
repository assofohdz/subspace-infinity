// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Wire-byte pinning tests for {@link ShipTypeId} and {@link ConsumableTypeId}.
 *
 * <p>The byte values are the wire identifiers used in {@code
 * GameSession#avatar(byte)} and {@code GameSession#action(byte)} RMI
 * dispatch. A silent renumber would corrupt every running client/server
 * pair without a compile-time signal. These tests are the explicit
 * compile-time fence — any byte-value change here must be a conscious
 * test-update + version bump, not a drive-by enum rename.
 */
public class WireTypeIdTest {

  @Test
  public void shipTypeId_wireBytes_matchSubspaceCanon() {
    assertEquals("SPEC", (byte) 0, ShipTypeId.SPEC.wireId());
    assertEquals("WARBIRD", (byte) 1, ShipTypeId.WARBIRD.wireId());
    assertEquals("JAVELIN", (byte) 2, ShipTypeId.JAVELIN.wireId());
    assertEquals("SPIDER", (byte) 3, ShipTypeId.SPIDER.wireId());
    assertEquals("LEVI", (byte) 4, ShipTypeId.LEVI.wireId());
    assertEquals("TERRIER", (byte) 5, ShipTypeId.TERRIER.wireId());
    assertEquals("WEASEL", (byte) 6, ShipTypeId.WEASEL.wireId());
    assertEquals("LANCASTER", (byte) 7, ShipTypeId.LANCASTER.wireId());
    assertEquals("SHARK", (byte) 8, ShipTypeId.SHARK.wireId());
  }

  @Test
  public void shipTypeId_fromWireId_roundTripsForEveryEnumConstant() {
    for (final ShipTypeId t : ShipTypeId.values()) {
      assertEquals("round-trip " + t, t, ShipTypeId.fromWireId(t.wireId()));
    }
  }

  @Test
  public void shipTypeId_fromWireId_unknownByte_throws() {
    try {
      ShipTypeId.fromWireId((byte) 99);
      fail("Expected IllegalArgumentException for byte 99");
    } catch (final IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void consumableTypeId_wireBytes_matchHistoricalServerValues() {
    assertEquals("PLACEBRICK", (byte) 0, ConsumableTypeId.PLACEBRICK.wireId());
    assertEquals("FIREBURST", (byte) 1, ConsumableTypeId.FIREBURST.wireId());
    assertEquals("PLACEDECOY", (byte) 2, ConsumableTypeId.PLACEDECOY.wireId());
    assertEquals("PLACEPORTAL", (byte) 3, ConsumableTypeId.PLACEPORTAL.wireId());
    assertEquals("REPEL", (byte) 4, ConsumableTypeId.REPEL.wireId());
    assertEquals("FIREROCKET", (byte) 5, ConsumableTypeId.FIREROCKET.wireId());
    assertEquals("FIRETHOR", (byte) 6, ConsumableTypeId.FIRETHOR.wireId());
    assertEquals("WARP", (byte) 7, ConsumableTypeId.WARP.wireId());
  }

  @Test
  public void consumableTypeId_fromWireId_roundTripsForEveryEnumConstant() {
    for (final ConsumableTypeId t : ConsumableTypeId.values()) {
      assertEquals("round-trip " + t, t, ConsumableTypeId.fromWireId(t.wireId()));
    }
  }

  @Test
  public void consumableTypeId_fromWireId_unknownByte_throws() {
    try {
      ConsumableTypeId.fromWireId((byte) 99);
      fail("Expected IllegalArgumentException for byte 99");
    } catch (final IllegalArgumentException expected) {
      // expected
    }
  }
}
