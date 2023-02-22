/*
 * Copyright (c) 2018, Asser Fahrenholz
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

import com.jme3.math.ColorRGBA;

/**
 * @author Asser
 */
public enum Bombs {
  BOMB_1(1, 12, ColorRGBA.Red, 25),
  BOMB_2(2, 11, ColorRGBA.Yellow, 30),
  BOMB_3(3, 10, ColorRGBA.Blue, 35),
  BOMB_4(4, 9, ColorRGBA.White, 40),
  EMP_1(1, 8, ColorRGBA.Red, 15),
  EMP_2(2, 7, ColorRGBA.Yellow, 20),
  EMP_3(3, 6, ColorRGBA.Blue, 25),
  EMP_4(4, 5, ColorRGBA.White, 30),
  SUPER_1(1, 4, ColorRGBA.Red, 35),
  SUPER_2(2, 3, ColorRGBA.Yellow, 40),
  SUPER_3(3, 2, ColorRGBA.Blue, 45),
  SUPER_4(4, 1, ColorRGBA.White, 50),
  THOR(1, 0, ColorRGBA.Black, 10);

  /** Level value */
  public final int level;

  /** Offset in the bm2 file */
  public final int viewOffset;

  /** Light color */
  public final ColorRGBA lightColor;

  public final float lightRadius;

  Bombs(
      final int level, final int viewOffset, final ColorRGBA lightColor, final float lightRadius) {
    this.level = level;
    this.viewOffset = viewOffset;
    this.lightColor = lightColor;
    this.lightRadius = lightRadius;
  }
}
