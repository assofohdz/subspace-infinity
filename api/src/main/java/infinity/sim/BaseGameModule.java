// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import com.simsilica.sim.AbstractGameSystem;

/**
 * Base class for pluggable server-side game modules.
 *
 * @author Asser
 */
// NOTE: instantiated by the future GroovyModuleLoader. The legacy AdaptiveLoader
// (custom ClassLoader + reflection-based ~startModule chat command) was retired;
// see .scratch/groovy-module-loader/PRD.md. Until that loader exists,
// concrete subclasses live in modules/ but have no runtime instantiator.
public abstract class BaseGameModule extends AbstractGameSystem {

    private final ChatHostedPoster chp;
    private final AccountManager am;
    private final ArenaManager arenas;
    private final TimeManager time;
    private final PhysicsManager physics;

    public BaseGameModule(final ChatHostedPoster chp, final AccountManager am,
            final ArenaManager arenas, final TimeManager time, final PhysicsManager physics) {
        this.chp = chp;
        this.am = am;
        this.arenas = arenas;
        this.time = time;
        this.physics = physics;
    }

    public ChatHostedPoster getChp() {
        return chp;
    }

    public AccountManager getAm() {
        return am;
    }

    public ArenaManager getArenas() {
        return arenas;
    }

    public TimeManager getTimeManager() {
        return time;
    }

    public PhysicsManager getPhysicsManager() {
        return physics;
    }

}
