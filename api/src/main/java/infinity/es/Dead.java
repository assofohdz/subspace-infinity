// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * The object is dead if it has this component.
 *
 * @author Paul Speed
 */
public class Dead implements EntityComponent {
    private final long time;

    public Dead() {
        this(0L);
    }

    public Dead(final long time) {
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "Dead[at:" + time + "]";
    }
}
