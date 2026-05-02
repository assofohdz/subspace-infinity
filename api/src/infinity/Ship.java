/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity;

import infinity.sim.util.InfinityRunTimeException;

/**
 * Wire-protocol identity for the eight playable ships. Order is warbird,
 * javelin, spider, leviathan, terrier, weasel, lancaster, shark — the byte
 * id is what flows through {@code ShipType} on the network.
 *
 * <p>Sprite-sheet offsets and other client-side render data live in
 * {@code infinity.client.view.ShipVisuals}, kept out of the api layer per
 * {@code api-contracts.md}.
 *
 * @author asser
 */
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
