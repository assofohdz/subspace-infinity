// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

import infinity.sim.CategoryFilter;

/**
 * Component carrying the collision category filter for this entity.
 *
 * @author AFahrenholz
 */
public class CollisionCategory implements EntityComponent {

    private final CategoryFilter filter;

    public CollisionCategory() {
        this(null);
    }

    public CollisionCategory(final CategoryFilter filter) {
        this.filter = filter;
    }

    public CategoryFilter getFilter() {
        return filter;
    }
}
