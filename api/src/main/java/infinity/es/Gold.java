// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the gold/score value carried by this entity.
 *
 * @author ss
 */
public class Gold implements EntityComponent {

    private final int gold;

    public Gold() {
        this(0);
    }

    public Gold(final int gold) {
        this.gold = gold;

    }

    public int getGold() {
        return gold;
    }

}
