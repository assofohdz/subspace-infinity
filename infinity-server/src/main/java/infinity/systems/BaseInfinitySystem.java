// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;

/** {@link AbstractGameSystem} + {@link #requireSystem(Class)} non-null lookup helper. Opt-in. */
public abstract class BaseInfinitySystem extends AbstractGameSystem {

    /** Throws {@link IllegalStateException} when the system isn't registered. */
    protected final <T> T requireSystem(final Class<T> type) {
        final T result = getSystem(type);
        if (result == null) {
            throw new IllegalStateException(
                getClass().getName() + " system requires the " + type.getSimpleName() + ".");
        }
        return result;
    }
}
