// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.view;

import com.jme3.math.ColorRGBA;

/**
 * Client-side look-and-feel for the radar HUD — bundles every colour and
 * sizing knob the radar pipeline reads at one place. {@link #DEFAULT} carries
 * the shipping look (Continuum-style muddy-green arena interior, darker
 * void surrounding loaded arenas, grey block silhouettes, white-self /
 * green-friend / red-enemy / grey-neutral team palette, 218 px HUD
 * footprint). A future HUD-theming system can construct alternative
 * {@link RadarTheme} instances (color-blind palettes, compact / classic /
 * large size profiles, dark / light variants, etc.) without touching the
 * radar's rendering code.
 *
 * <p>Internal projection constants ({@code RADAR_CAM_HEIGHT},
 * {@code RADAR_CAM_NEAR}/{@code FAR}, {@code DEFAULT_RANGE_WORLD_UNITS}) stay
 * on {@code RadarState} — they aren't user-facing aesthetics, just numbers
 * that need to clear all expected geometry. Likewise the
 * {@code BodyPosition} ring-buffer depth is a SimEthereal-side convention,
 * not a radar tuning knob.
 *
 * @param voidTintColor radar fill OUTSIDE any loaded arena footprint — set
 *     on the off-screen viewport's clear colour. Slice U1: arena interior
 *     fills render on top, so what's left showing is the "void" between
 *     loaded arenas
 * @param arenaTintColor interior fill colour for the closed-polygon footprint
 *     of the arena that currently contains the local avatar (the "you are
 *     here" arena). Footprint geometry is carried by {@code ArenaFootprint}
 *     components, stamped server-side by {@code ArenaSystem}
 * @param arenaOutlineColor boundary colour for the same current-arena
 *     footprint — drawn as a {@code Mesh.Mode.Lines} loop on top of the
 *     interior fill
 * @param arenaTintColorMuted interior fill colour for any other loaded
 *     arena (i.e. the avatar is NOT inside it). Defaults to a 50%-saturation
 *     copy of {@code arenaTintColor} via luminance lerp — keeps the radar
 *     readable when many arenas are loaded by visually de-emphasising the
 *     ones the player is not currently inside. Per-frame point-in-polygon
 *     test in {@code RadarState} drives the branch
 * @param arenaOutlineColorMuted boundary colour for the same not-current
 *     arenas; defaults to a 50%-saturation copy of {@code arenaOutlineColor}
 * @param blockColor solid-tile silhouette colour
 * @param selfColor blip colour for the local avatar
 * @param friendlyColor blip colour for entities sharing the local avatar's
 *     {@code Frequency}
 * @param enemyColor blip colour for entities on a different {@code Frequency}
 * @param neutralColor blip colour for entities with no {@code Frequency}
 *     component (prizes, neutral statics)
 * @param pixelSize edge of the off-screen radar texture and its GUI quad in
 *     pixels — controls the HUD footprint
 * @param canonicalRangeWorldUnits radar range at which {@code RadarBlipFactory}
 *     mesh sizes render at their nominal pixel size; blips scale uniformly by
 *     {@code currentRange / canonicalRangeWorldUnits} so on-screen blip size
 *     stays constant regardless of zoom
 * @param shipDotRadius radius of the disc used for ship blips, in world units
 *     at {@code canonicalRangeWorldUnits} zoom
 * @param dotSegments fan smoothness for the ship-blip disc — higher = rounder
 * @param staticBlipHalfSize half-extent of square / diamond static blips
 *     (flag, prize), in world units at {@code canonicalRangeWorldUnits} zoom
 *
 * @author Asser Fahrenholz
 */
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

  /**
   * Builds {@link #DEFAULT} from base palette colours, deriving the muted
   * variants by 50%-saturation desaturation of the full-strength tint /
   * outline. Centralising the math here makes tuning the mute amount a
   * one-line change (the {@code 0.5f} factor) and keeps the muted defaults
   * in sync if the base colours are ever retuned.
   */
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

  /**
   * Returns a copy of {@code src} desaturated toward its luminance gray by
   * {@code saturationFactor}. {@code 1.0} = unchanged; {@code 0.0} = fully
   * gray (luminance only); {@code 0.5} = halfway between the original and
   * gray, which is the convention used for the default muted arena
   * variants. Luminance uses ITU-R BT.601 weights (0.299 R + 0.587 G + 0.114
   * B) — the same convention used elsewhere in jME for grayscale
   * conversion.
   *
   * <p>Package-private rather than nested-private so theme authors writing
   * a custom {@code RadarTheme} can reuse the exact same desaturation rule
   * for their arena-muted variants.
   */
  static ColorRGBA desaturate(final ColorRGBA src, final float saturationFactor) {
    final float lum = 0.299f * src.r + 0.587f * src.g + 0.114f * src.b;
    return new ColorRGBA(
        lum + (src.r - lum) * saturationFactor,
        lum + (src.g - lum) * saturationFactor,
        lum + (src.b - lum) * saturationFactor,
        src.a);
  }
}
