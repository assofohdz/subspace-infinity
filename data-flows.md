# Data flows

High-level data flows in Subspace Infinity. Each section pairs an
**AS-IS** view (today's codebase) with a **TO-BE** view (post-[ADR-0008](docs/adr/0008-arena-composition-and-modules.md)
arena composition + modules) so the shape of the change is visible at a
glance.

Diagrams are Mermaid; render natively on GitHub and in most IDEs (VS
Code: Markdown Preview Mermaid Support; JetBrains: Mermaid plugin).

This page is **descriptive**, not normative — the canonical decisions
live in CONTEXT.md and `docs/adr/`. If a diagram disagrees with an ADR,
the ADR wins.

---

## 1. Arena load — from `arena.groovy` to a live arena

### AS-IS

```mermaid
flowchart LR
  subgraph zone[zone/]
    AG[arena.groovy]
    FRAG[conf/&lt;preset&gt;/*.groovy fragments]
    SHIPS[ships.groovy]
  end

  subgraph load[Load orchestration]
    GAL[GroovyArenaLoader]
    GSL[GroovyShipLoader]
    GFL[GroovyFragmentLoader]
  end

  subgraph state[Per-arena state]
    AC[ArenaConfig record]
    CR[ConfigRegistry]
    INI[Ini-style fragment data]
  end

  subgraph sys[Server systems]
    AS[ArenaSystem]
    CRS[ConfigRegistrySystem]
    SS[SettingsSystem]
  end

  AG --> GAL --> AC --> AS
  SHIPS --> GSL --> CR --> CRS
  FRAG --> GFL --> INI --> SS
  AS -. on load .-> CRS
```

**Reads:** `arena.groovy` is settings only (map, shipsScript,
includeFragment, wallFriction, spawners, shipRestrictions). Arena
behaviour beyond settings = whatever core systems are wired in
`GameServer`. No per-arena modules, no gametype awareness.

### TO-BE

```mermaid
flowchart LR
  subgraph zone[zone/]
    AG[arena.groovy<br/>+ module statements]
    PRESETS[presets/&lt;name&gt;/*.groovy<br/>module bundles]
    FRAG[conf/&lt;preset&gt;/*.groovy<br/>settings fragments]
    SHIPS[ships.groovy]
  end

  subgraph load[Load orchestration]
    GAL[GroovyArenaLoader<br/>extended]
    GSL[GroovyShipLoader]
    GFL[GroovyFragmentLoader]
    ML[ModuleLoader<br/>helper]
    MC[ModuleCatalog<br/>fixed Java catalog]
  end

  subgraph state[Per-arena state]
    AC[ArenaConfig<br/>+ modules]
    CR[ConfigRegistry]
    INI[Ini-style fragment data]
    AMS_DATA[ArenaModuleSet]
  end

  subgraph sys[Server systems]
    AS[ArenaSystem]
    CRS[ConfigRegistrySystem]
    SS[SettingsSystem]
    AMS[ArenaModuleSystem]
    COORD[Score / WinCondition<br/>Coordinator systems]
    DISP[ArenaLifecycleDispatcherSystem]
  end

  AG --> GAL --> AC
  PRESETS -. usePreset .-> GAL
  SHIPS --> GSL --> CR --> CRS
  FRAG --> GFL --> INI --> SS
  AC --> AS
  AC -- arena.modules() --> ML
  MC -. lookup .-> ML
  ML --> AMS_DATA --> AMS
  AS -. on load .-> CRS
  AS -. on load .-> AMS
  AMS -. fail-fast .-> AS
```

**Reads:** `arena.groovy` now declares both settings AND modules. The
`ModuleLoader` validates module identifiers against the explicit
`ModuleCatalog` and assembles a fully-validated `ArenaModuleSet` before
the arena is considered loaded. Unknown id / duplicate single-pick /
missing-dep / invalid-config all fail loudly during load, not at first
tick. Future external-Groovy modules append to `ModuleCatalog` at zone
start (Phase 3 — separate flow).

---

## 2. Server tick — what writes what, in what order

### AS-IS

```mermaid
sequenceDiagram
  participant Core as Core systems
  participant ECS as ECS components
  participant Decay as DecaySystem
  Note over Core,Decay: Systems run in GameServer registration order
  Core->>ECS: read last-tick components
  Core->>ECS: write canonical components<br/>(per ADR-0001 single-writer rule)
  Core->>ECS: emit *Change entities for mutation
  Note over Core,ECS: Canonical writers drain their *Change EntitySets
  Decay->>ECS: reap expired entities<br/>(Decay deadline passed)
```

**Reads:** Each canonical writer system owns one component type. Other
systems emit `*Change` entities (carrying `ChangeTarget` + payload);
the writer drains, applies, deletes (or unwinds on `Decay` reap).
No notion of game-event categories beyond the registered systems
themselves. No round/match structure.

### TO-BE

```mermaid
sequenceDiagram
  participant AMS as ArenaModuleSystem
  participant ECS as ECS components
  participant SC as ScoreCoordinator
  participant WCC as WinConditionCoordinator
  participant DISP as ArenaLifecycleDispatcher

  Note over AMS,DISP: System registration order enforces phase order

  rect rgba(220,240,255,0.6)
    Note over AMS,ECS: Phase 1 — Mechanics
    AMS->>ECS: each mechanic publishes state<br/>(CrownOwnership, FlagOwnership,<br/>BallPossession, OutsidePlayArea, …)
  end

  rect rgba(230,255,230,0.6)
    Note over AMS,ECS: Phase 2 — Module contributions
    AMS->>ECS: each scoring module reads mechanic state +<br/>game events, emits ScoreContribution
    AMS->>ECS: each winCondition module emits<br/>WinConditionTrigger
  end

  rect rgba(255,245,225,0.6)
    Note over SC,WCC: Phase 3 — Coordinators<br/>(canonical writers per ADR-0001)
    SC->>ECS: drain ScoreContribution → sum →<br/>write PlayerScore + TeamScore
    WCC->>ECS: drain WinConditionTrigger → OR →<br/>set RoundEndPending marker
  end

  rect rgba(255,230,240,0.6)
    Note over DISP,ECS: Phase 4 — Lifecycle dispatch<br/>(only on round-end tick)
    DISP->>ECS: read RoundEndPending markers
    DISP->>AMS: onRoundEnd(arenaId, roundNum, outcome)<br/>on each module
    Note over DISP,AMS: matchStructure may also fire onMatchEnd
    DISP->>AMS: onMatchStart / onRoundStart<br/>for next iteration
    DISP->>ECS: remove RoundEndPending markers
  end
```

**Reads:** Same single-writer-per-component discipline as today, but
scaled up: many modules emit *transient contributions*, one
*coordinator* per layered category writes the canonical component.
Mechanics publish state in phase 1 so phase 2 modules read fully-
published mechanic state. Phase 4 only runs on round-end ticks; the
dispatcher reads the coordinator's marker and calls lifecycle hooks
directly (no EventBus indirection).

---

## 3. Communication channels — three planes, picked by shape

### AS-IS (already per [ADR-0003](docs/adr/0003-communication-channels.md))

```mermaid
flowchart LR
  subgraph req[Channel A — ECS transient component]
    direction TB
    A1[Producer system] -->|setComponent<br/>EnergyChange, ChangeTarget| A2[Transient entity]
    A2 -->|EntitySet drains<br/>same tick or next| A3[Canonical writer]
    A3 -->|applies to target| A4[Long-lived component]
  end

  subgraph arena[Channel B — Arena event<br/>via EventBus]
    direction TB
    B1[Server system] -->|publish<br/>ShipEvent | B2[EventBus arena.*]
    B2 -->|listeners filter<br/>by ArenaId| B3[Listeners: HUD, score log,<br/>module rebroadcast]
  end

  subgraph zone[Channel C — Zone event<br/>via EventBus]
    direction TB
    C1[Server system] -->|publish<br/>AccountEvent | C2[EventBus zone.*]
    C2 -->|process-global| C3[Lobby UI,<br/>account services]
  end
```

**Reads:** Three planes for three shapes of communication. Mutation
requests = Channel A (queryable, atomic). Arena-scoped notifications =
Channel B (HUD, replay, score log). Zone-scoped lifecycle = Channel C
(login, account, master server).

### TO-BE — modules use all three, with no new channels

```mermaid
flowchart LR
  subgraph mod[ArenaModule]
    M1[Module logic]
  end

  subgraph req[Channel A — ECS transient]
    A1[ScoreContribution<br/>WinConditionTrigger<br/>FlagPickup, etc.]
  end

  subgraph arena[Channel B — Arena event]
    B1[ShipEvent, KillEvent,<br/>RoundStarted, …]
  end

  subgraph life[Direct lifecycle dispatch<br/>NOT EventBus]
    L1[ArenaLifecycleDispatcherSystem]
  end

  M1 -->|emit contributions| A1
  M1 -->|subscribe at onArenaLoad<br/>unsubscribe at onArenaUnload| B1
  M1 -->|tick: read EntitySets| req
  L1 -->|onMatchStart<br/>onRoundStart<br/>onRoundEnd<br/>onMatchEnd| M1
```

**Reads:** Modules read ECS components like any system and subscribe
to the per-arena EventBus for arena events — no new game-event hooks
on `ArenaModule`. The only direct-dispatch surface is *lifecycle* hooks
(load/match/round/unload) — these are framework calls, not bus events,
because their atomicity inside one tick is load-bearing for round-reset
correctness. This preserves CONTEXT.md's *Module extensibility
principle*: powerful tools, trust the module.

---

## 4. Hot-reload — three flavours

### AS-IS

```mermaid
flowchart LR
  FILE[arena.groovy or<br/>conf/&lt;preset&gt;/*.groovy] -->|mtime poll<br/>every N seconds| WATCH[File watcher<br/>in ArenaSystem]
  WATCH -->|on change| RELOAD[ConfigRegistrySystem.load<br/>arenaId arenaConfig]
  RELOAD -->|atomic swap| CR[New ConfigRegistry snapshot]
  CR -. consumers re-read .-> CONSUME[Consuming systems]
  RELOAD -. ships.groovy only .-> REPROJECT[ShipSpawnSystem.reprojectAll]
```

**Reads:** Settings hot-reload is poll-based, atomic-swap. Ships re-
project so live ships pick up new stats without respawn. No notion of
module-set diffs because there are no modules.

### TO-BE — three flavours, all on the same mtime poll

```mermaid
flowchart TD
  FILE[arena.groovy] -->|mtime poll| WATCH[File watcher]
  WATCH -->|parse new ArenaConfig| DIFF{Diff vs<br/>current state}

  DIFF -->|kwargs changed| ALPHA[α Config-diff<br/>onConfigReloaded newConfig<br/>on the affected module<br/>NO teardown]
  DIFF -->|module added or removed| BETA[β Module-set-diff<br/>onArenaLoad on new<br/>onArenaUnload on removed<br/>cleanup contract enforced]
  DIFF -->|map line changed| GAMMA[γ Map swap<br/>existing ~swapMap flow<br/>spawnPlacement re-runs<br/>modules NOT torn down]

  ALPHA --> RUN[Arena keeps running]
  BETA --> RUN
  GAMMA --> RUN
```

**Reads:** Three flavours of hot-reload, all triggered by the same
mtime poll. (α) is the cheapest — just kwargs change, module instance
updates internally, no player disruption. (β) is the test bed for the
future Phase-3 Groovy loader; the cleanup contract on `onArenaUnload`
ensures removed modules don't leak components. (γ) reuses the existing
`~swapMap` flow.

---

## 5. Phase-3 future — external-author Groovy modules

Diagrams above are Phase 1 + Phase 2 of [`arena-modules/PRD.md`](.scratch/arena-modules/PRD.md). The
Phase-3 extension surface ([`groovy-module-loader/PRD.md`](.scratch/groovy-module-loader/PRD.md)) appends
to the framework without changing it:

```mermaid
flowchart LR
  AUTHOR[zone/&lt;author-modules&gt;/&lt;name&gt;/module.groovy] -->|compile on<br/>zone start| LOADER[GroovyModuleLoader]
  LOADER -->|register in| MC[ModuleCatalog<br/>existing]
  MC -. arena.groovy references<br/>by string id .-> AG[arena.groovy]
  AG -. arena load .-> SAME[ArenaModuleSystem<br/>same framework, no changes]
```

**Reads:** Phase 3 doesn't add a parallel loader path; it appends to
the same catalog the Phase-1 framework already consults. Arena authors
reference external modules by string id exactly like built-in ones.
This is what makes the framework worth landing first — Phase 3's job
shrinks to compile + register.

---

## See also

- [ADR-0008](docs/adr/0008-arena-composition-and-modules.md) — the decision shape behind the TO-BE diagrams.
- [ADR-0001](docs/adr/0001-ecs-component-model.md) — canonical writer rule that the coordinator pattern preserves.
- [ADR-0003](docs/adr/0003-communication-channels.md) — three communication planes (depicted in section 3).
- [ADR-0004](docs/adr/0004-settings-pipeline.md) — settings pipeline hot-reload (extended in section 4).
- [`.scratch/arena-modules/PRD.md`](.scratch/arena-modules/PRD.md) — implementation PRD for the framework + first consumer.
- [`.scratch/groovy-module-loader/PRD.md`](.scratch/groovy-module-loader/PRD.md) — Phase-3 external-author extension PRD.
- `CONTEXT.md` — glossary for every term in these diagrams.
