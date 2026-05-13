// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Deferred-action timer in SimTime nanos; pairs with {@link infinity.systems.DelaySystem}. See ADR 0007. */
public class Delay implements EntityComponent {

    public static final String SET = "set";
    public static final String REMOVE = "remove";

    private final long startTime;
    private final long endTime;
    private final Set<EntityComponent> delayedComponents;
    private final String type;

    public Delay() {
        this(0L, 0L, null, null);
    }

    public Delay(final long startTime, final long endTime,
                 final Set<EntityComponent> delayedComponents, final String type) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.delayedComponents = delayedComponents;
        this.type = type;
    }

    /** Convenience factory mirroring {@code Decay.duration(now, ms)} — endTime = now + durationMillis. */
    public static Delay duration(final long simNowNanos, final long durationMillis,
                                 final Set<EntityComponent> delayedComponents, final String type) {
        return new Delay(
                simNowNanos,
                simNowNanos + TimeUnit.NANOSECONDS.convert(durationMillis, TimeUnit.MILLISECONDS),
                delayedComponents,
                type);
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public double getPercent(final long simNowNanos) {
        final long delta = endTime - startTime;
        if (delta <= 0L) {
            return 1.0;
        }
        return (double) (simNowNanos - startTime) / delta;
    }

    public boolean isElapsed(final long simNowNanos) {
        return simNowNanos >= endTime;
    }

    public Set<EntityComponent> getDelayedComponents() {
        return delayedComponents;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Delay[" + ((endTime - startTime) / 1_000_000.0) + " ms]";
    }
}
