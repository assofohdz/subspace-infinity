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
package infinity.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class PhysicsMassTypes_old {

    /**
     * Indicates a normal mass
     */
    public static final String NORMAL = "Normal";

    /**
     * Indicates that the mass is infinite (rate of rotation and translation
     * should not change)
     */
    public static final String INFINITE = "Infinite";

    /**
     * Indicates that the mass's rate of rotation should not change
     */
    public static final String FIXED_ANGULAR_VELOCITY = "Fixed angular velocity";

    /**
     * Indicates that the mass's rate of translation should not change
     */
    public static final String FIXED_LINEAR_VELOCITY = "Fixed linear velocity";

    /**
     * Indicates that the mass is normal, but we want CCD enabled
     */
    public static final String NORMAL_BULLET = "Normal_bullet";

    public static PhysicsMassType_old normal(EntityData ed) {
        return PhysicsMassType_old.create(NORMAL, ed);
    }

    public static PhysicsMassType_old infinite(EntityData ed) {
        return PhysicsMassType_old.create(INFINITE, ed);
    }

    public static PhysicsMassType_old fixedAngularVelocity(EntityData ed) {
        return PhysicsMassType_old.create(FIXED_ANGULAR_VELOCITY, ed);
    }

    public static PhysicsMassType_old fixedLinearVelocity(EntityData ed) {
        return PhysicsMassType_old.create(FIXED_LINEAR_VELOCITY, ed);
    }

    public static PhysicsMassType_old normal_bullet(EntityData ed) {
        return PhysicsMassType_old.create(NORMAL_BULLET, ed);
    }

}
