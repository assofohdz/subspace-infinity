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

import infinity.BombLevel;

/**
 * Client-side sprite-sheet offsets for each {@link BombLevel} level. Pure render
 * data — kept out of the api {@code BombLevel} enum so the wire-protocol identity
 * stays free of jME-side concerns. (The previous {@code lightColor} /
 * {@code lightRadius} fields on {@code BombLevel} were dropped — only the
 * commented-out {@code GameEntities} call referenced them, and the live
 * lighting pipeline uses {@code SISpatialFactory}'s own logic.)
 */
public enum BombVisuals {
  BOMB_1(BombLevel.BOMB_1, 12),
  BOMB_2(BombLevel.BOMB_2, 11),
  BOMB_3(BombLevel.BOMB_3, 10),
  BOMB_4(BombLevel.BOMB_4, 9);

  public final BombLevel level;
  public final int viewOffset;

  BombVisuals(final BombLevel level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }

  /** Look up the visuals for a given bomb level. */
  public static BombVisuals forLevel(final BombLevel level) {
    for (final BombVisuals v : values()) {
      if (v.level == level) {
        return v;
      }
    }
    throw new IllegalArgumentException("No BombVisuals for " + level);
  }
}
