// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class ActionTypes {

    public static final String REPEL = "repel"; // Fast moving projectile
    public static final String WARP = "warp"; // Fast moving projectile
    public static final String PORTAL = "portal"; // Fast moving projectile
    public static final String DECOY = "decoy"; // Fast moving projectile
    public static final String ROCKET = "rocket"; // Fast moving projectile
    public static final String BRICK = "brick"; // Fast moving projectile
    public static final String ATTACH = "attach"; // Fast moving projectile

    public static ActionType repel(final EntityData ed) {
        return ActionType.create(REPEL, ed);
    }

    public static ActionType warp(final EntityData ed) {
        return ActionType.create(WARP, ed);
    }

    public static ActionType portal(final EntityData ed) {
        return ActionType.create(PORTAL, ed);
    }

    public static ActionType decoy(final EntityData ed) {
        return ActionType.create(DECOY, ed);
    }

    public static ActionType rocket(final EntityData ed) {
        return ActionType.create(ROCKET, ed);
    }

    public static ActionType brick(final EntityData ed) {
        return ActionType.create(BRICK, ed);
    }

    public static ActionType attach(final EntityData ed) {
        return ActionType.create(ATTACH, ed);
    }
}
