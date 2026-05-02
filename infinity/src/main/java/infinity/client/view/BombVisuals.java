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

import infinity.Bombs;

/**
 * Client-side sprite-sheet offsets for each {@link Bombs} level. Pure render
 * data — kept out of the api {@code Bombs} enum so the wire-protocol identity
 * stays free of jME-side concerns. (The previous {@code lightColor} /
 * {@code lightRadius} fields on {@code Bombs} were dropped — only the
 * commented-out {@code GameEntities} call referenced them, and the live
 * lighting pipeline uses {@code SISpatialFactory}'s own logic.)
 */
public enum BombVisuals {
  BOMB_1(Bombs.BOMB_1, 12),
  BOMB_2(Bombs.BOMB_2, 11),
  BOMB_3(Bombs.BOMB_3, 10),
  BOMB_4(Bombs.BOMB_4, 9);

  public final Bombs level;
  public final int viewOffset;

  BombVisuals(final Bombs level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }

  /** Look up the visuals for a given bomb level. */
  public static BombVisuals forLevel(final Bombs level) {
    for (final BombVisuals v : values()) {
      if (v.level == level) {
        return v;
      }
    }
    throw new IllegalArgumentException("No BombVisuals for " + level);
  }
}
