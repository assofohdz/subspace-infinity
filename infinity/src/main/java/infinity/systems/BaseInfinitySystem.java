// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;

/**
 * Project-local base class for server-side game systems that need shared
 * lookup helpers on top of Sio2's {@link AbstractGameSystem}.
 *
 * <p>Subclasses get {@link #requireSystem(Class)} — a non-null lookup
 * that replaces the recurring {@code getSystem(X) + null-check + throw}
 * boilerplate with a single call. Per architectural review (Tier 3
 * finding #7), the boilerplate was duplicated across 20+ system
 * initializers; folding it into one place makes the throw message
 * uniform and log-greppable by class name + {@code "requires the"}.
 *
 * <p>This subclass exists because Sio2's {@link AbstractGameSystem} is
 * external — we cannot add a default method on it. We also avoid a
 * static utility (e.g. {@code Systems.require(this::getSystem, X.class)})
 * because the inherited-method form keeps call sites short
 * ({@code requireSystem(X.class)}) at the cost of one base-class swap.
 *
 * <p>Systems that do not need the helpers may continue to extend
 * {@link AbstractGameSystem} directly; this class is opt-in.
 */
public abstract class BaseInfinitySystem extends AbstractGameSystem {

    /**
     * Look up a registered game system, throwing an
     * {@link IllegalStateException} with a uniform message if absent.
     *
     * <p>Same fail-fast intent as Sio2's {@code getSystem(type, true)},
     * with a uniform message format
     * ({@code "<class> system requires the <type>."}) — the format
     * mirrors the most common shape of the pre-helper boilerplate so
     * existing log greps continue to fire on
     * {@code class.getName() + " system requires the "}.
     *
     * <p>{@link IllegalStateException} (a JDK type) replaces the mix of
     * {@code InfinityRunTimeException} / {@link IllegalStateException}
     * the pre-helper boilerplate used. Both are unchecked and the
     * pre-helper sites were never caught — only thrown at init-fault
     * time — so the type swap does not affect runtime behaviour.
     *
     * @param <T>  the system type
     * @param type the system class to look up
     * @return the registered system; never {@code null}
     * @throws IllegalStateException if no system of the requested type
     *     is registered with the {@code GameSystemManager}.
     */
    protected final <T> T requireSystem(final Class<T> type) {
        final T result = getSystem(type);
        if (result == null) {
            throw new IllegalStateException(
                getClass().getName() + " system requires the " + type.getSimpleName() + ".");
        }
        return result;
    }
}
