# Bot AI v3 — review punch-list: correctness, tuning-knob compliance, cleanup, docs sync

Status: ready-for-agent
Category: maintenance

## Why

v2 (closed by [Bot AI v2 PRD](../bot-ai-v2/PRD.md), all 8 substrate slices ✅)
delivered the planner, capability derivation, flow-field navigation, spatial
fields, arena objectives + roles, and the debug HUD. A thorough four-agent
code review of the whole subsystem (api/ contracts, server spatial/nav,
server brain/tactical/perception, config/legacy/docs) on **2026-05-26**
found the architecture sound — clean api→server layering, Config-Component
Projection followed, unified input pipeline confirmed (bots write
`MovementInput` through `PlayerDriver`, no parallel NPC path), no EntitySet
leaks, capability-axis principle intact, nav/Dijkstra math trustworthy with
safe threading.

What it found are **loose ends, not foundations**: a few real bugs, a
cluster of tuning knobs that escaped into Java (ADR-0006 / ADR-0014
violations), one hot-path config read (ADR-0002), dead code awaiting
deletion (~5,200 LOC), and widespread doc/ADR/tracker drift (five ADRs say
`Proposed` for fully-landed code). v3 is the punch-list that closes them.

This PRD is **not** the behaviour catalog (that workstream continues in
[bot-behaviour-catalog](../bot-behaviour-catalog/PRD.md)). v3 is pure
hardening of the v1+v2 substrate.

## Architecture anchors

No new ADRs. v3 enforces existing ones the review found violated:

- [ADR-0002](../../docs/adr/0002-config-component-projection.md) — hot-path consumers read components, not templates (Issue #03).
- [ADR-0006](../../docs/adr/0006-tuning-knobs-vs-magic-numbers.md) — tuning knobs live in Groovy (Issue #02).
- [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md) — legacy retirement open-work (Issue #04); status flip (Issue #06).
- [ADR-0013](../../docs/adr/0013-bot-tactical-goal-layer.md) / [ADR-0014](../../docs/adr/0014-capability-derived-bot-composition.md) — `BotDerivationConfig` open-work (Issue #02); status flips (Issue #06).
- [ADR-0015](../../docs/adr/0015-arena-objective-and-roles.md) — `BotRole` round-reset invariant (Issue #01).

## Implementation slices

Dependency-ordered. Issues #01–#03 are code-correctness/compliance; #04–#05
are cleanup; #06 is docs/tracker hygiene (can land anytime, no code dep).

| # | Slice | Severity | Depends on | Status |
|---|-------|----------|------------|--------|
| 01 | Correctness bugs (NPE, target-thrash, role-refresh) | CRITICAL/HIGH | — | ✅ done (role-refresh: option (a) documented; v2.x event-driven reassignment still deferred) |
| 02 | Tuning-knob migration to Groovy (~12 knobs + `BotDerivationConfig`) | HIGH | — | ✅ done (A + B+C+D + E + F.7); F.1–F.5 deferred to [B12](BACKLOG.md#b12--api-steer-action--brain-wander-constants-v3-02f1f5-deferred); F.6 false alarm |
| 03 | Hot-path config read → project `perceptionRadius` to component | HIGH | — | ✅ done (projected onto BrainWiring at refreshDerivation boundary; redundant ArenaId/BotRole/ShipType reads deferred — not ADR-0002 violations) |
| 04 | Legacy package + dead-path deletion (~5,200 LOC) | HIGH | — | ready-for-agent |
| 05 | Naming + dead-component cleanup (`MoverState`, `Steering*`) | HIGH/MEDIUM | — | ready-for-agent |
| 06 | Docs/ADR + tracker sync | MEDIUM | — | ready-for-agent |

See `.scratch/bot-ai-v3/issues/01-06.md`. Carried-over / un-promoted work
(v1 flocking, deferred review notes) lives in [BACKLOG.md](BACKLOG.md).

## Out of scope

- **New behaviours** — belong to the [behaviour catalog](../bot-behaviour-catalog/).
- **Multi-bot squad coordination, online learning, wormhole-aware nav** — still v2.x+/v3+ per the v2 PRD out-of-scope list.
- **Performance micro-opts** flagged LOW by the review (heap-entry allocation in Dijkstra, `ConcurrentHashMap`→`HashMap` in `AsyncNavigationFields`, disc-iteration sqrt avoidance) — captured as notes inside the relevant issues, not promoted to their own slices.

## Done definition

- Issues #01–#05 land with acceptance criteria met, PMD ratchet on touched files, layer test + build green.
- Issue #06 flips ADR-0009/0010/0013/0014 status, syncs the `replacement-as-mutation.md` snapshot, and reconciles the v2 PRD done-criteria + retirement-issue status.
- No behaviour change for end users beyond the three bug fixes in #01.
