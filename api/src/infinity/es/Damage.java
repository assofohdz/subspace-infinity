// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.ext.mphys.ShapeInfo;

/**
 * This component is carried by the bomb and holds the intended damage (of the producer). The
 * actual damage is calculated by the bomb's explosion (when knowing the consumer).
 *
 * @author Asser
 */
public class Damage implements EntityComponent {

    private final long explosionDecay;
    private final int intendedDamage;
    private final ShapeInfo explosionShape;

    public Damage() {
        this(0L, 0, null);
    }

    public Damage(final long explosionDecay, final int intendedDamage, final ShapeInfo explosionShape) {
        this.explosionDecay = explosionDecay;
        this.intendedDamage = intendedDamage;
        this.explosionShape = explosionShape;
    }

    public ShapeInfo getExplosionShape() {
        return explosionShape;
    }

    public long getExplosionDecay() {
        return explosionDecay;
    }

    public int getIntendedDamage() {
        return intendedDamage;
    }
}
