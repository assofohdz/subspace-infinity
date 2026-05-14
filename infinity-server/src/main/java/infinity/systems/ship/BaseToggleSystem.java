// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.es.ActiveToggle;
import infinity.es.ChangeTarget;
import infinity.es.ToggleChange;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Generic canonical writer for stateful boolean toggle components (status-family {@code CloakActive}/{@code StealthActive}/{@code XRadarActive}/{@code AntiwarpActive}). Drains a {@link ToggleChange} value-replacement stream; caches previous value on Decay-bound adds so the on-remove revert restores the prior state. The concrete subclass supplies the two {@code Class} keys + a {@code boolean -> new XActive(boolean)} lambda so the {@code new XActive(...)} constructor call remains visible to {@code CanonicalWriterTest}. See ADR 0001. */
public abstract class BaseToggleSystem<C extends ActiveToggle, D extends ToggleChange>
        extends BaseInfinitySystem {

    private final Class<C> activeClass;
    private final Class<D> changeClass;
    private final Function<Boolean, C> ctor;

    private EntityData ed;
    private EntitySet changes;

    private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

    private record TrackedApply(EntityId target, boolean previousValue) {}

    protected BaseToggleSystem(
            final Class<C> activeClass,
            final Class<D> changeClass,
            final Function<Boolean, C> ctor) {
        this.activeClass = activeClass;
        this.changeClass = changeClass;
        this.ctor = ctor;
    }

    @Override
    protected void initialize() {
        ed = requireSystem(EntityData.class);
        changes = ed.getEntities(changeClass, ChangeTarget.class);
    }

    @Override
    protected void terminate() {
        changes.release();
        changes = null;
    }

    @Override
    public void update(final SimTime time) {
        changes.applyChanges();
        drainChanges();
    }

    private void drainChanges() {
        final List<EntityId> oneShotHolders = new ArrayList<>();
        for (final Entity added : changes.getAddedEntities()) {
            final ChangeTarget ct = added.get(ChangeTarget.class);
            final boolean newValue = added.get(changeClass).newValue();
            final Decay decay = ed.getComponent(added.getId(), Decay.class);
            final boolean previous = applyValue(ct.target(), newValue);
            if (decay == null) {
                oneShotHolders.add(added.getId());
            } else {
                trackedApplied.put(added.getId(), new TrackedApply(ct.target(), previous));
            }
        }
        for (final EntityId id : oneShotHolders) {
            ed.removeEntity(id);
        }
        for (final Entity removed : changes.getRemovedEntities()) {
            final TrackedApply applied = trackedApplied.remove(removed.getId());
            if (applied == null) {
                continue;
            }
            applyValue(applied.target(), applied.previousValue());
        }
    }

    /** Apply {@code newValue}; return previous value for the reverse-on-remove cache. */
    private boolean applyValue(final EntityId target, final boolean newValue) {
        final C current = ed.getComponent(target, activeClass);
        final boolean previous = current != null && current.isActive();
        if (previous == newValue) {
            return previous;
        }
        ed.setComponent(target, ctor.apply(newValue));
        return previous;
    }
}
