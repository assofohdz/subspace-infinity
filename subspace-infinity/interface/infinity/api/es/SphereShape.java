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
package infinity.api.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/**
 *
 * @author Asser Fahrenholz
 */
public class SphereShape implements EntityComponent {

    private double radius;
    private Vec3d offset;

    /**
     * For SpiderMonkey serialization purposes.
     */
    protected SphereShape() {
    }

    /**
     * Creates a new sphere collision shape with the specified radius and
     * 'center' relative to the natural origin of the object to which it
     * applies. -CoG in most cases.
     *
     * @param radius the radius of the sphere
     * @param centerOffset the offset relative to the object to which it applies
     */
    public SphereShape(double radius, Vec3d centerOffset) {
        this.radius = radius;
        this.offset = centerOffset;
    }

    public SphereShape(double radius) {
        this.radius = radius;
        this.offset = new Vec3d();
    }

    public double getRadius() {
        return radius;
    }

    public Vec3d getCenterOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return "SphereShape[radius=" + radius + ", centerOffset=" + offset + "]";
    }
}
