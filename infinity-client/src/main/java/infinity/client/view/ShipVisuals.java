// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

import infinity.Ship;

/**
 * Client-side sprite-sheet row offsets for each {@link Ship}. Pure render data
 * — kept out of the api {@code Ship} enum so the wire-protocol identity stays
 * free of jME-side concerns. The constant names mirror {@code Ship} so the
 * lookup is direct: {@code ShipVisuals.WARBIRD.visualOffset}.
 */
public enum ShipVisuals {
  WARBIRD(Ship.WARBIRD, 31),
  JAVELIN(Ship.JAVELIN, 27),
  SPIDER(Ship.SPIDER, 23),
  LEVIATHAN(Ship.LEVIATHAN, 19),
  TERRIER(Ship.TERRIER, 15),
  WEASEL(Ship.WEASEL, 11),
  LANCASTER(Ship.LANCASTER, 7),
  SHARK(Ship.SHARK, 3);

  public final Ship ship;
  public final int visualOffset;

  ShipVisuals(final Ship ship, final int visualOffset) {
    this.ship = ship;
    this.visualOffset = visualOffset;
  }

  /** Look up the visuals for a given ship. */
  public static ShipVisuals forShip(final Ship ship) {
    for (final ShipVisuals v : values()) {
      if (v.ship == ship) {
        return v;
      }
    }
    throw new IllegalArgumentException("No ShipVisuals for " + ship);
  }
}
