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
