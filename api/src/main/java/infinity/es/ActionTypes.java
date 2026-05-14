// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

/** Action type names; routed through {@code EntityData}'s string index. */
public class ActionTypes {

    private ActionTypes() { /* utility class */ }

    public static final String REPEL = "repel"; // Fast moving projectile
    public static final String WARP = "warp"; // Fast moving projectile
    public static final String PORTAL = "portal"; // Fast moving projectile
    public static final String DECOY = "decoy"; // Fast moving projectile
    public static final String ROCKET = "rocket"; // Fast moving projectile
    public static final String BRICK = "brick"; // Fast moving projectile
    public static final String ATTACH = "attach"; // Fast moving projectile
}
