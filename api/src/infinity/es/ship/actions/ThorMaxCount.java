// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Maximum number of Thor's Hammers allowed in ships
 *
 * @author Asser
 */
public class ThorMaxCount implements EntityComponent {

    private final int max;

    public ThorMaxCount() {
        this(0);
    }

    public ThorMaxCount(final int count) {
        max = count;
    }

    public int getCount() {
        return max;
    }
}
