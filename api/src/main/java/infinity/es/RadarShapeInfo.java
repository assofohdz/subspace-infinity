// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/** Radar blip shape name (string-indexed via {@code EntityData}); entities without the component are not drawn on the radar. */
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
