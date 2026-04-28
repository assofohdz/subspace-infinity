# Add support for LVZ files

Status: ready-for-human
Cross-ref: [GH #107](https://github.com/assofohdz/subspace-infinity/issues/107)

Implement an LVZ (client-side visual overlay) loader/parser. Currently `MapSystem.loadMap`'s `.lvz` path is a placeholder.

LVZ is the Subspace/Continuum overlay format — sprite sheets + show/hide rules driven by server events. Needed by the [`MatchLvz`](../subspace-module-archetypes/issues/11-match-lvz.md) module (scoreboards, timers, banners) and any other arena overlay UX.

## Comments
