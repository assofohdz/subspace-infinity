// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 * Names the radar blip shape used to represent this entity on the client-side
 * radar viewport. The string is keyed into {@link EntityData#getStrings()} so
 * the wire-form is a small int — same convention as {@code ShapeInfo.shapeName}
 * for the main scene view.
 *
 * <p>Server-authored only (added by spawn systems). Clients resolve the name
 * via a registry at attach time to produce a flat 2D blip Geometry. Color is
 * NOT carried here — it's resolved client-side from the blip's
 * {@link Frequency} vs. the local player's frequency, which keeps re-skinning
 * (color-blind palettes, themes) a client-only concern.
 *
 * <p>Entities without this component are simply not drawn on the radar; this
 * is how projectiles, particles, and other "invisible to radar" things opt
 * out without an extra suppression flag.
 */
public class RadarShapeInfo implements EntityComponent {

    private final int shapeName;

    protected RadarShapeInfo() {
        this(0);
    }

    public RadarShapeInfo(final int shapeName) {
        this.shapeName = shapeName;
    }

    public static RadarShapeInfo create(final String shapeName, final EntityData ed) {
        return new RadarShapeInfo(ed.getStrings().getStringId(shapeName, true));
    }

    public int getShapeName() {
        return shapeName;
    }

    public String getShapeName(final EntityData ed) {
        return ed.getStrings().getString(shapeName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[shapeName=" + shapeName + "]";
    }
}
