// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

/** Status-toggle wire-byte constants. */
public final class StatusSystem {

    private StatusSystem() {}

    public static final byte ANTIWARP_ENABLE = 0x0;
    public static final byte ANTIWARP_DISABLE = 0x1;
    public static final byte CLOAK_ENABLE = 0x2;
    public static final byte CLOAK_DISABLE = 0x3;
    public static final byte STEALTH_ENABLE = 0x4;
    public static final byte STEALTH_DISABLE = 0x5;
    public static final byte XRADAR_ENABLE = 0x6;
    public static final byte XRADAR_DISABLE = 0x7;

}
