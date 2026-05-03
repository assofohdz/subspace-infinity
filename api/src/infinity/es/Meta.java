// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding metadata such as the entity's creation timestamp.
 *
 * @author asser
 */
public class Meta implements EntityComponent {

    private final long timeCreated;

    public Meta() {
        this(0L);
    }

    public Meta(final long timeCreated) {
        this.timeCreated = timeCreated;
    }

    public long getTimeCreated() {
        return timeCreated;
    }
}
