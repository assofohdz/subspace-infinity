// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity;

import infinity.sim.util.InfinityRunTimeException;

/** Wire-protocol identity for the eight playable ships; byte id flows through {@code ShipType}. */
public enum Ship {
  WARBIRD(1, "ship_warbird"),
  JAVELIN(2, "ship_javelin"),
  SPIDER(3, "ship_spider"),
  LEVIATHAN(4, "ship_leviathan"),
  TERRIER(5, "ship_terrier"),
  WEASEL(6, "ship_weasel"),
  LANCASTER(7, "ship_lancaster"),
  SHARK(8, "ship_shark");

  private final byte id;
  private final String name;

  Ship(final int id, final String name) {
    this.id = (byte) id;
    this.name = name;
  }

  public byte getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public static Ship getShip(final byte id) {
    for (final Ship ship : values()) {
      if (ship.getId() == id) {
        return ship;
      }
    }
    throw new InfinityRunTimeException("No ship with id " + id);
  }

  public static Ship getShip(final String name) {
    for (final Ship ship : values()) {
      if (ship.getName().equals(name)) {
        return ship;
      }
    }
    throw new InfinityRunTimeException("No ship with name " + name);
  }
}
