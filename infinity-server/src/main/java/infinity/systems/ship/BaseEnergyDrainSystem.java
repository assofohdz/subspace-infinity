// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.ActiveToggle;
import infinity.es.EnergyDrain;
import infinity.es.ship.Energy;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.List;

/** Generic per-tick energy-drain runner. Subclasses register one or more {@link DrainEntry} bindings; the base walks each entry's {@link EntitySet} and, for every entity whose toggle reports {@code isActive()}, applies {@code rate × tpf} drain via {@link EnergySystem#damage}. Today's sole subclass is {@code StatusDrainSystem} for the 4 status-family toggles; future drain consumers (e.g. afterburner) plug in by adding a sibling subclass. */
public abstract class BaseEnergyDrainSystem extends BaseInfinitySystem {

    private EntityData ed;
    private EnergySystem energySystem;
    private final List<DrainEntry> drainEntries = new ArrayList<>();

    /** One drain binding: an EntitySet keyed on (toggleClass, statsClass, Energy.class) + the two class refs used to read the per-entity active state and drain rate. */
    protected record DrainEntry(
            EntitySet set,
            Class<? extends ActiveToggle> toggleClass,
            Class<? extends EnergyDrain> statsClass) {}

    @Override
    protected final void initialize() {
        ed = requireSystem(EntityData.class);
        energySystem = requireSystem(EnergySystem.class);
        registerDrainEntries();
    }

    @Override
    protected final void terminate() {
        for (final DrainEntry de : drainEntries) {
            de.set().release();
        }
        drainEntries.clear();
    }

    @Override
    public final void update(final SimTime time) {
        final double tpf = time.getTpf();
        for (final DrainEntry de : drainEntries) {
            de.set().applyChanges();
            drain(de, tpf);
        }
    }

    /** Subclass hook: build one or more {@code (toggle, stats)} bindings via {@link #addDrainEntry}. Called once during {@link #initialize}. */
    protected abstract void registerDrainEntries();

    protected final <T extends ActiveToggle, S extends EnergyDrain> void addDrainEntry(
            final Class<T> toggleClass, final Class<S> statsClass) {
        drainEntries.add(new DrainEntry(
                ed.getEntities(toggleClass, statsClass, Energy.class), toggleClass, statsClass));
    }

    private void drain(final DrainEntry de, final double tpf) {
        for (final Entity e : de.set()) {
            if (!e.get(de.toggleClass()).isActive()) {
                continue;
            }
            final double rate = e.get(de.statsClass()).energyDrainPerSecond();
            final int amount = perTickDrain(rate, tpf);
            if (amount > 0) {
                energySystem.damage(e.getId(), -amount);
            }
        }
    }

    /** Per-tick integer Energy drain from {@code energy/sec × tpf}, rounded half-up. */
    static int perTickDrain(final double energyDrainPerSecond, final double tpfSeconds) {
        if (energyDrainPerSecond <= 0.0 || tpfSeconds <= 0.0) {
            return 0;
        }
        return (int) Math.round(energyDrainPerSecond * tpfSeconds);
    }
}
