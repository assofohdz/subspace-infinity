// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import com.jme3.network.service.AbstractHostedService;

/**
 * Base class for pluggable hosted services.
 *
 * @author Asser
 */
// NOTE: instantiated by the future GroovyModuleLoader. The legacy AdaptiveLoader
// was retired; see .scratch/groovy-module-loader/PRD.md. No concrete
// subclasses exist today; kept as scaffolding for the planned Groovy port.
public abstract class BaseGameService extends AbstractHostedService {

    private final ChatHostedPoster chp;
    private final AccountManager am;
    private final ArenaManager arenas;

    public BaseGameService(final ChatHostedPoster chp, final AccountManager am,
            final ArenaManager arenas) {
        this.chp = chp;
        this.am = am;
        this.arenas = arenas;
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
}
