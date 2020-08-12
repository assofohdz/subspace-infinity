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
public class ToggleTypes {

    public static final String MULTI = "repel"; // Fast moving projectile
    public static final String ANTI = "warp"; // Fast moving projectile
    public static final String STEALTH = "portal"; // Fast moving projectile
    public static final String CLOAK = "decoy"; // Fast moving projectile
    public static final String XRADAR = "rocket"; // Fast moving projectile

    public static ToggleType multi(EntityData ed) {
        return ToggleType.create(MULTI, ed);
    }

    public static ToggleType anti(EntityData ed) {
        return ToggleType.create(ANTI, ed);
    }

    public static ToggleType stealth(EntityData ed) {
        return ToggleType.create(STEALTH, ed);
    }

    public static ToggleType cloak(EntityData ed) {
        return ToggleType.create(CLOAK, ed);
    }

    public static ToggleType xradar(EntityData ed) {
        return ToggleType.create(XRADAR, ed);
    }
}
