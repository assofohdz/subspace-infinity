// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

/** Toggle type names; routed through {@code EntityData}'s string index. */
public class ToggleTypes {

    private ToggleTypes() { /* utility class */ }

    public static final String MULTI = "repel"; // Fast moving projectile
    public static final String ANTI = "warp"; // Fast moving projectile
    public static final String STEALTH = "portal"; // Fast moving projectile
    public static final String CLOAK = "decoy"; // Fast moving projectile
    public static final String XRADAR = "rocket"; // Fast moving projectile
}
