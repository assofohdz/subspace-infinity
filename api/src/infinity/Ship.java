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
 * This enum holds both byte information and string names for the ships in the game.
 * The order of the ships are warbird, javelin, spider, leviathan, terrier, weasel, lancaster, shark.
 *
 * @author asser
 */
public enum Ship {
  WARBIRD(1, "ship_warbird", 31),
    JAVELIN(2, "ship_javelin", 27),
    SPIDER(3, "ship_spider", 23),
    LEVIATHAN(4, "ship_leviathan",19),
    TERRIER(5, "ship_terrier",15),
    WEASEL(6, "ship_weasel", 11),
    LANCASTER(7, "ship_lancaster",7),
    SHARK(8, "ship_shark",3);

    private final byte id;
    private final String name;
    private final int visualOffset;

    Ship(final int id, final String name, int visualOffset) {
        this.id = (byte) id;
        this.name = name;
        this.visualOffset = visualOffset;
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

  public int getVisualOffset() {
    return visualOffset;
  }
}
