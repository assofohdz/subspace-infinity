// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.view;

import com.jme3.math.ColorRGBA;

/** Look-and-feel bundle for the radar HUD — colours + sizing knobs read by the radar pipeline. */
public record RadarTheme(
    ColorRGBA voidTintColor,
    ColorRGBA arenaTintColor,
    ColorRGBA arenaOutlineColor,
    ColorRGBA arenaTintColorMuted,
    ColorRGBA arenaOutlineColorMuted,
    ColorRGBA blockColor,
    ColorRGBA selfColor,
    ColorRGBA friendlyColor,
    ColorRGBA enemyColor,
    ColorRGBA neutralColor,
    int pixelSize,
    double canonicalRangeWorldUnits,
    float shipDotRadius,
    int dotSegments,
    float staticBlipHalfSize) {

  /** Shipping default — Continuum-aesthetic muddy-green arenas on a darker void. */
  public static final RadarTheme DEFAULT = createDefault();

  private static RadarTheme createDefault() {
    final ColorRGBA arenaTint = new ColorRGBA(0.12f, 0.20f, 0.10f, 1f);
    final ColorRGBA arenaOutline = new ColorRGBA(0.40f, 0.65f, 0.30f, 1f);
    return new RadarTheme(
        /* voidTintColor */            new ColorRGBA(0.06f, 0.10f, 0.05f, 1f),
        /* arenaTintColor */           arenaTint,
        /* arenaOutlineColor */        arenaOutline,
        /* arenaTintColorMuted */      desaturate(arenaTint, 0.5f),
        /* arenaOutlineColorMuted */   desaturate(arenaOutline, 0.5f),
        /* blockColor */               new ColorRGBA(0.55f, 0.55f, 0.55f, 1f),
        /* selfColor */                ColorRGBA.White,
        /* friendlyColor */            ColorRGBA.Green,
        /* enemyColor */               ColorRGBA.Red,
        /* neutralColor */             ColorRGBA.Gray,
        /* pixelSize */                218,
        /* canonicalRangeWorldUnits */ 256.0,
        /* shipDotRadius */            5f,
        /* dotSegments */              16,
        /* staticBlipHalfSize */       10f);
  }

  /** Desaturate {@code src} toward its BT.601 luminance gray; {@code 1.0} = unchanged, {@code 0.0} = gray. */
  static ColorRGBA desaturate(final ColorRGBA src, final float saturationFactor) {
    final float lum = 0.299f * src.r + 0.587f * src.g + 0.114f * src.b;
    return new ColorRGBA(
        lum + (src.r - lum) * saturationFactor,
        lum + (src.g - lum) * saturationFactor,
        lum + (src.b - lum) * saturationFactor,
        src.a);
  }
}
