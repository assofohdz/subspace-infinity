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

/**
 * Client-side sprite-sheet offsets for the special-bomb variants (EMP, Super,
 * Thor). Relocated from the former api {@code BombRegistry} enum, which was
 * client-only data leaking into the api layer. Only {@link #THOR} is currently
 * referenced by {@code SISpatialFactory}; the EMP and Super variants are kept
 * as scaffolding for the planned weapon-variant feature (parallel to the
 * unwired one-shot sprite-shader stub).
 *
 * <p>The previous {@code lightColor} / {@code lightRadius} fields were dropped
 * — only the constructor assignments referenced them, no consumer ever read.
 */
public enum SpecialBombVisuals {
  EMP_1(1, 8),
  EMP_2(2, 7),
  EMP_3(3, 6),
  EMP_4(4, 5),
  SUPER_1(1, 4),
  SUPER_2(2, 3),
  SUPER_3(3, 2),
  SUPER_4(4, 1),
  THOR(1, 0);

  /** Variant level (1-4 within the EMP / Super families; 1 for Thor). */
  public final int level;

  /** Offset in the bm2 sprite sheet. */
  public final int viewOffset;

  SpecialBombVisuals(final int level, final int viewOffset) {
    this.level = level;
    this.viewOffset = viewOffset;
  }
}
