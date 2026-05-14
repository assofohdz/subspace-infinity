// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the bounty value awarded for destroying this entity.
 *
 * @author Asser
 */
public class Bounty implements EntityComponent {

    private final int value;

    public Bounty() {
        this(0);
    }

    public Bounty(final int bounty) {
        this.value = bounty;
    }

    public int getBounty() {
        return value;
    }
}
