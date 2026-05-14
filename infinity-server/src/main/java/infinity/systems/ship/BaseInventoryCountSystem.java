// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.DeltaChange;
import infinity.es.ship.actions.InventoryCap;
import infinity.es.ship.actions.InventoryCount;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/** Generic canonical writer for inventory-count components (Burst, Brick, Decoy, Portal, Repel, Rocket, ThorCurrentCount). Drains a {@link DeltaChange} stream, clamps at {@link InventoryCap#max()} above and {@code 0} below. The concrete subclass supplies the three {@code Class} keys + a {@code count -> new XXX(count)} lambda so the {@code new XXX(...)} constructor call remains visible to {@code CanonicalWriterTest}. See ADR 0001. */
public abstract class BaseInventoryCountSystem<
        C extends InventoryCount, D extends DeltaChange, S extends InventoryCap>
        extends BaseInfinitySystem {

    private final Class<C> countClass;
    private final Class<D> changeClass;
    private final Class<S> statsClass;
    private final IntFunction<C> countCtor;

    private EntityData ed;
    private EntitySet changes;

    private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

    private record TrackedApply(EntityId target, int delta) {}

    protected BaseInventoryCountSystem(
            final Class<C> countClass,
            final Class<D> changeClass,
            final Class<S> statsClass,
            final IntFunction<C> countCtor) {
        this.countClass = countClass;
        this.changeClass = changeClass;
        this.statsClass = statsClass;
        this.countCtor = countCtor;
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
        final Map<EntityId, Integer> deltaByTarget = new HashMap<>();
        final List<EntityId> oneShotHolders = new ArrayList<>();
        for (final Entity added : changes.getAddedEntities()) {
            final ChangeTarget ct = added.get(ChangeTarget.class);
            final int delta = added.get(changeClass).delta();
            deltaByTarget.merge(ct.target(), delta, Integer::sum);
            if (ed.getComponent(added.getId(), Decay.class) == null) {
                oneShotHolders.add(added.getId());
            } else {
                trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
            }
        }

        for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
            applyDelta(e.getKey(), e.getValue());
        }
        for (final EntityId id : oneShotHolders) {
            ed.removeEntity(id);
        }
        for (final Entity removed : changes.getRemovedEntities()) {
            final TrackedApply applied = trackedApplied.remove(removed.getId());
            if (applied == null) {
                continue;
            }
            applyDelta(applied.target(), -applied.delta());
        }
    }

    private void applyDelta(final EntityId target, final int delta) {
        final S stats = ed.getComponent(target, statsClass);
        if (stats == null || stats.max() <= 0) {
            return;
        }
        final C current = ed.getComponent(target, countClass);
        final int currentCount = current == null ? 0 : current.count();
        final int proposed = currentCount + delta;
        final int clamped = Math.max(0, Math.min(proposed, stats.max()));
        if (clamped == currentCount) {
            return;
        }
        ed.setComponent(target, countCtor.apply(clamped));
    }
}
