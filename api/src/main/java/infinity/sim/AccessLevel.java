// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

/**
 * Access tier enum used to gate chat commands and admin actions.
 *
 * @author Asser
 */
public enum AccessLevel {

    PLAYER_LEVEL(0, "Player"), BOT_LEVEL(1, "Bot"), OUTSIDER_LEVEL(2, "Outside"), LR_LEVEL(3, "LR"), ZH_LEVEL(4, "ZH"),
    ER_LEVEL(5, "ER"), MODERATOR_LEVEL(6, "Mod"), HIGHMOD_LEVEL(7, "HighMod"), DEV_LEVEL(8, "Developer"),
    SMOD_LEVEL(9, "SMod"), SYSOP_LEVEL(10, "Sysop"), OWNER_LEVEL(11, "Owner");

    /**
     * Level value
     */
    public final int level;

    /**
     * Offset in the bm2 file
     */
    public final String text;

    private AccessLevel(final int level, final String text) {
        this.level = level;
        this.text = text;
    }

    @Override
    public String toString() {
        return text + " [lvl " + level + "]";
    }

}
