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

    private int shapeName;

    protected RadarShapeInfo() {
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
