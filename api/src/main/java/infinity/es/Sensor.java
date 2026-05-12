// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/** Marker for sensor bodies — generates contact events but {@code ContactSystem} disables collision response. Discriminate sensor kinds via sibling components. */
public class Sensor implements EntityComponent {

    public Sensor() {
        // marker; no state.
    }

    @Override
    public String toString() {
        return "Sensor";
    }
}
