// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.DeltaChange;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Generic canonical writer for weapon-level components (BombCurrentLevel/BulletCurrentLevel/MineCurrentLevel). Drains a {@link DeltaChange} stream as additive ordinal delta on the level enum, clamping at the stats record's per-ship cap level above and {@code 0} below. A {@code null} {@code currentLevel} means "ship not allowed this weapon" — skip. The concrete subclass supplies the three {@code Class} keys + the enum {@code values()} array + accessor lambdas + a {@code level -> new XCurrentLevel(level)} lambda so the {@code new XCurrentLevel(...)} constructor call remains visible to {@code CanonicalWriterTest}. See ADR 0001. */
public abstract class BaseWeaponLevelSystem<
        C extends EntityComponent,
        D extends DeltaChange,
        S extends EntityComponent,
        L extends Enum<L>>
        extends BaseInfinitySystem {

    private final Class<C> currentClass;
    private final Class<D> changeClass;
    private final Class<S> statsClass;
    private final L[] values;
    private final Function<C, L> currentLevelFn;
    private final Function<S, L> capLevelFn;
    private final Function<L, C> ctor;

    private EntityData ed;
    private EntitySet changes;

    private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

    private record TrackedApply(EntityId target, int delta) {}

    // 8 params: 3 Class refs + values array + 3 accessor lambdas + ctor lambda. Each is irreducible domain info.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    protected BaseWeaponLevelSystem(
            final Class<C> currentClass,
            final Class<D> changeClass,
            final Class<S> statsClass,
            final L[] values,
            final Function<C, L> currentLevelFn,
            final Function<S, L> capLevelFn,
            final Function<L, C> ctor) {
        this.currentClass = currentClass;
        this.changeClass = changeClass;
        this.statsClass = statsClass;
        this.values = values.clone();
        this.currentLevelFn = currentLevelFn;
        this.capLevelFn = capLevelFn;
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
        final C current = ed.getComponent(target, currentClass);
        if (current == null || currentLevelFn.apply(current) == null) {
            return;
        }
        final S stats = ed.getComponent(target, statsClass);
        final int currentOrdinal = currentLevelFn.apply(current).ordinal();
        final int proposed = currentOrdinal + delta;
        final L capLevel = stats == null ? null : capLevelFn.apply(stats);
        final int capOrdinal = capLevel == null ? values.length - 1 : capLevel.ordinal();
        final int clamped = Math.max(0, Math.min(proposed, capOrdinal));
        if (clamped == currentOrdinal) {
            return;
        }
        ed.setComponent(target, ctor.apply(values[clamped]));
    }
}
