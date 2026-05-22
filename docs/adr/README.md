# Architecture Decision Records

Architectural decisions that shape Subspace Infinity. Each ADR captures the *decision shape* + alternatives + consequences. Operational *enforcement* lives in [`.claude/rules/`](../../.claude/rules/) and tests; ADRs are the rationale anchors those rules point at.

## Reading order

ADRs are mostly independent but some build on each other. Suggested read order for a new contributor:

1. [**0001** — ECS component model](./0001-ecs-component-model.md) — Continuous + Stats split; one canonical writer per component; Change-entity mutation. The substrate everything else rests on.
2. [**0005** — Layered architecture](./0005-layered-architecture.md) — api / server / client / modules; server-authoritative client read-only. The boundary every other ADR respects.
3. [**0002** — Config-Component Projection (CCP)](./0002-config-component-projection.md) — `*Config` template tier + ECS component tier + spawn-time projection. Where tuning lives at runtime.
4. [**0003** — Communication channels](./0003-communication-channels.md) — intent components / EventBus / domain-specific (Channel C). Pick by shape, not topic.
5. [**0004** — `zone/` extension surface](./0004-settings-pipeline.md) — settings pipeline + Groovy module loader. How operators + community authors extend the game.
6. [**0006** — Tuning knobs vs. magic numbers](./0006-tuning-knobs-vs-magic-numbers.md) — the literal-promotion decision rule. Feeds 0004.
7. [**0007** — Entity TTL via `Decay`](./0007-entity-ttl-decay.md) — single mechanism, deadline-shaped, multi-writer exception.
8. [**0008** — Arena composition & modules](./0008-arena-composition-and-modules.md) — gametypes as emergent compositions of horizontal modules; `ArenaModule` lifecycle + 4-phase tick + coordinator pattern; arena.groovy as pure data over a fixed Java module catalog.

## Index

| # | Title | Status | Date |
|---|---|---|---|
| [0001](./0001-ecs-component-model.md) | ECS component model: Continuous + Stats with Change-entity mutation | Accepted | 2026-05-11 |
| [0002](./0002-config-component-projection.md) | Config-Component Projection: tuning templates project to ECS components at spawn time | Proposed | 2026-05-13 |
| [0003](./0003-communication-channels.md) | Communication channels: intent components for mutation, EventBus for announcement | Proposed | 2026-05-13 |
| [0004](./0004-settings-pipeline.md) | `zone/` extension surface: settings pipeline + module loader | Proposed | 2026-05-13 |
| [0005](./0005-layered-architecture.md) | Layered architecture: api purity, server authority, client read-only | Proposed | 2026-05-13 |
| [0006](./0006-tuning-knobs-vs-magic-numbers.md) | Tuning knobs vs. magic numbers: the literal-promotion decision | Proposed | 2026-05-13 |
| [0007](./0007-entity-ttl-decay.md) | Entity TTL: one mechanism, deadline-shaped (`Decay`) | Proposed | 2026-05-13 |
| [0008](./0008-arena-composition-and-modules.md) | Arena composition: gametypes as compositions of horizontal modules | Proposed | 2026-05-15 |
| [0009](./0009-bot-ai-architecture.md) | Bot AI architecture: layered hand-roll with Behaviour Trees | Proposed | 2026-05-22 |

## Backlog

Implicit decisions not yet formalised live in [`.scratch/adr-backlog.md`](../../.scratch/adr-backlog.md) with explicit trigger conditions. That backlog is intentionally short — most implicit decisions can stay implicit until churn justifies the write-up.

## Template

ADRs follow a consistent shape: **Context** (the problem) → **Decision** (what we chose) → **Consequences** (positive / costs / neutral) → **Alternatives considered** → **Resolved decisions** (TL;DR) → **Open work** (enforcement / migration items) → **References**.

New ADRs should match the existing shape and length (≈150-200 lines for major decisions; ≈100 lines for narrower ones). Long-form ADRs are deliberate — they double as agent-readable context, not just human reference.
