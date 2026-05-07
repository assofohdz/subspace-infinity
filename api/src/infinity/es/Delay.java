// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import java.util.Set;

import com.simsilica.es.EntityComponent;

/**
 * Represents a time-to-live for an entity.
 *
 * @author Asser Fahrenholz
 */
public class Delay implements EntityComponent {

    public final static String SET = "set";
    public final static String REMOVE = "remove";

    private final long start;
    private final long delta;
    private final Set<EntityComponent> delayedComponents;
    private final String type;

    public Delay() {
        this(0L, null, null);
    }

    public Delay(final long deltaMillis, final Set<EntityComponent> delayedComponents, final String type) {
        start = System.nanoTime();
        delta = deltaMillis * 1000000;
        this.delayedComponents = delayedComponents;
        this.type = type;
    }

    public double getPercent() {
        final long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    public Set<EntityComponent> getDelayedComponents() {
        return delayedComponents;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Delay[" + (delta / 1000000.0) + " ms]";
    }
}
