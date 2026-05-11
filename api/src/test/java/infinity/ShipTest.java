// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.ext.mphys.ShapeInfo;
import infinity.es.ShapeNames;
import org.junit.Test;

/**
 * Wire-byte regression tests for {@link Ship} and
 * {@link ShapeNames#createShip(byte, EntityData, double)}.
 *
 * <p>Discovered while scoping the B1 protocol-enum migration: the
 * {@link Ship} enum, the server-side {@code AvatarSystem} byte constants,
 * the {@code shapeNameForShipType} switch on that class, and this api/
 * {@code ShapeNames.createShip} switch had a four-way disagreement on
 * bytes 6 and 7 (Weasel vs Lancaster). Pressing the in-game Weasel key
 * actually spawned a Lancaster, and vice versa. These tests pin the
 * canonical Subspace mapping — section order in {@code arena.conf} is
 * {@code [Warbird], [Javelin], [Spider], [Leviathan], [Terrier], [Weasel],
 * [Lancaster], [Shark]} → 1-indexed byte IDs:
 *
 * <ul>
 *   <li>1 → Warbird, 2 → Javelin, 3 → Spider, 4 → Leviathan,
 *   <li>5 → Terrier, 6 → Weasel, 7 → Lancaster, 8 → Shark.
 * </ul>
 *
 * <p>The byte ID is the only wire identifier flowing across RMI for
 * ship-type identity (see {@code GameSession#avatar(byte)}), so a swap
 * here is a real game bug and a permanent api-contract hazard if the
 * upcoming {@code ShipTypeId} enum promotion freezes it.
 */
public class ShipTest {

  /** Round-trip {@code Ship.getShip(s.getId()) == s} for every enum constant. */
  @Test
  public void getShip_byId_roundTripsForEveryEnumConstant() {
    for (final Ship s : Ship.values()) {
      assertEquals(
          "Round-trip mismatch for " + s + " (id=" + s.getId() + ")",
          s,
          Ship.getShip(s.getId()));
    }
  }

  /** Round-trip {@code Ship.getShip(s.getName()) == s} for every enum constant. */
  @Test
  public void getShip_byName_roundTripsForEveryEnumConstant() {
    for (final Ship s : Ship.values()) {
      assertEquals(
          "Name round-trip mismatch for " + s,
          s,
          Ship.getShip(s.getName()));
    }
  }

  /** Pin canonical byte IDs against the Subspace {@code arena.conf} ship-section order. */
  @Test
  public void shipEnumIds_matchSubspaceCanonicalOrder() {
    assertEquals("Warbird wire byte", (byte) 1, Ship.WARBIRD.getId());
    assertEquals("Javelin wire byte", (byte) 2, Ship.JAVELIN.getId());
    assertEquals("Spider wire byte", (byte) 3, Ship.SPIDER.getId());
    assertEquals("Leviathan wire byte", (byte) 4, Ship.LEVIATHAN.getId());
    assertEquals("Terrier wire byte", (byte) 5, Ship.TERRIER.getId());
    assertEquals("Weasel wire byte", (byte) 6, Ship.WEASEL.getId());
    assertEquals("Lancaster wire byte", (byte) 7, Ship.LANCASTER.getId());
    assertEquals("Shark wire byte", (byte) 8, Ship.SHARK.getId());
  }

  /**
   * Pin {@link ShapeNames#createShip(byte, EntityData, double)}'s byte → shape
   * mapping against canonical order. This is the test that flipped red →
   * green when the {@code case 6 / case 7} swap landed.
   */
  @Test
  public void createShip_byteToShapeName_matchesCanonicalOrder() {
    final EntityData ed = new DefaultEntityData();
    try {
      assertShapeId(ed, (byte) 1, ShapeNames.SHIP_WARBIRD);
      assertShapeId(ed, (byte) 2, ShapeNames.SHIP_JAVELIN);
      assertShapeId(ed, (byte) 3, ShapeNames.SHIP_SPIDER);
      assertShapeId(ed, (byte) 4, ShapeNames.SHIP_LEVI);
      assertShapeId(ed, (byte) 5, ShapeNames.SHIP_TERRIER);
      assertShapeId(ed, (byte) 6, ShapeNames.SHIP_WEASEL);
      assertShapeId(ed, (byte) 7, ShapeNames.SHIP_LANCASTER);
      assertShapeId(ed, (byte) 8, ShapeNames.SHIP_SHARK);
    } finally {
      ed.close();
    }
  }

  private static void assertShapeId(
      final EntityData ed, final byte wireByte, final String expectedShapeName) {
    final ShapeInfo info = ShapeNames.createShip(wireByte, ed, 1.0);
    assertNotNull("ShapeInfo for byte=" + wireByte, info);
    assertEquals(
        "Wrong shape for wire byte " + wireByte,
        expectedShapeName,
        info.getShapeName(ed));
  }
}
