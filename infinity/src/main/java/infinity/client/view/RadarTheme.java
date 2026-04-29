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

import com.jme3.math.ColorRGBA;

/**
 * Client-side palette for the radar HUD — bundles every colour the radar
 * pipeline reads at one place. {@link #DEFAULT} carries the shipping look
 * (Continuum-style muddy-green BG, grey block silhouettes, white-self / green-
 * friend / red-enemy / grey-neutral team palette). A future HUD-theming
 * system can construct alternative {@link RadarTheme} instances (color-blind
 * palettes, dark/light variants, etc.) without touching the radar's
 * rendering code.
 *
 * <p>Sizing constants (blip radius, radar pixel size, canonical range, etc.)
 * are deliberately NOT in this record — they're a separate axis of theming
 * (compact / classic / large) and can join later if a "size profile" concept
 * is needed.
 *
 * @param backgroundColor ambient fill inside the radar circle, set on the
 *     off-screen viewport's clear colour
 * @param blockColor solid-tile silhouette colour
 * @param selfColor blip colour for the local avatar
 * @param friendlyColor blip colour for entities sharing the local avatar's
 *     {@code Frequency}
 * @param enemyColor blip colour for entities on a different {@code Frequency}
 * @param neutralColor blip colour for entities with no {@code Frequency}
 *     component (prizes, neutral statics)
 *
 * @author Asser Fahrenholz
 */
public record RadarTheme(
    ColorRGBA backgroundColor,
    ColorRGBA blockColor,
    ColorRGBA selfColor,
    ColorRGBA friendlyColor,
    ColorRGBA enemyColor,
    ColorRGBA neutralColor) {

  /** Shipping default — the look the radar has had since #4 of the radar PRD landed. */
  public static final RadarTheme DEFAULT = new RadarTheme(
      /* backgroundColor */ new ColorRGBA(0.12f, 0.20f, 0.10f, 1f),
      /* blockColor */      new ColorRGBA(0.55f, 0.55f, 0.55f, 1f),
      /* selfColor */       ColorRGBA.White,
      /* friendlyColor */   ColorRGBA.Green,
      /* enemyColor */      ColorRGBA.Red,
      /* neutralColor */    ColorRGBA.Gray);
}
