# ADR backlog — potential future ADRs

Tracks architectural decisions that exist *implicitly* in the codebase but aren't yet formalized as ADRs. **Not a queue of "things to do."** Items here become draftable when (a) churn / change pressure surfaces the implicit decision, or (b) someone questions the existing shape in a review and needs the rationale.

The current series ([`docs/adr/`](../docs/adr/) 0001–0007) covers decisions that have been actively contested or migrated through. The backlog below is "wider-lens completeness" — areas where the *code does something* but the *why* is implicit. None are urgent.

Identified from a wider-lens architectural pass on 2026-05-13 (Nygard-style: "would someone reading this in six months wonder why?").

## Candidates

### Persistence strategy

- **Implicit today.** A `world.db` directory exists at the project root. Some state survives restarts, some doesn't. The boundary is not documented.
- **What an ADR would settle:**
  - What state is *durable* (account credentials, arena history, achievements?) vs *ephemeral* (live entities, in-flight projectiles, transient buffs).
  - Storage engine choice (currently SQLite via what driver? Why?).
  - Schema-migration story when fields evolve.
  - How persistence interacts with hot-reload — e.g. `ConfigRegistry` snapshots are in-memory; should there be a persisted last-known-good snapshot?
- **Trigger to draft.** First time someone proposes adding new persistent state and "should this be durable?" doesn't have an obvious answer. Also: if modules ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) need their own persistence, the trust-model + storage decisions compound.

### Authentication / accounts / identity flow

- **Implicit today.** `AccountHostedService` + `AccountEvent` ([ADR-0003 Channel B](../docs/adr/0003-communication-channels.md) publish sites). Session-to-player binding lives in code but no architectural decision document.
- **What an ADR would settle:**
  - Identity model: per-installation, per-account, guest, mixed?
  - Trust level differences between authenticated and unauthenticated sessions.
  - Module + identity intersection ([ADR-0004](../docs/adr/0004-settings-pipeline.md)): a module's server systems run with what identity? Whose actions does a module-emitted intent component carry?
  - Credentials storage (intersects the Persistence candidate above).
  - Login / logout lifecycle and the surface area for cheating.
- **Trigger to draft.** When modules ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) need identity-aware behavior (e.g. a `ScoringRule` tracking per-player stats), the implicit identity flow becomes load-bearing. Also: if anti-cheat work surfaces a concrete attack vector that the current identity model doesn't address.

### AI architecture

- **Implicit today.** `infinity.ai..` package contains AI / mob code. Layer rules treat it as a server-tier sub-package ([`LayerDependencyTest`](../infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java) Rule 2/3).
- **What an ADR would settle:**
  - Is `infinity.ai..` a special server-tier sub-layer, or just a server-tier system grouping?
  - Mob-behavior model — state machines, behavior trees, scripted-systems, ECS-native data-driven?
  - How modules ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) extend AI without forking core AI systems.
  - Path-finding strategy (per-tick vs cached; per-arena vs zone-global).
- **Trigger to draft.** First non-trivial AI extension by a module, or when mob types grow past a handful and the implicit pattern starts to creak.

### Wire-compatibility / component-shape migration policy

- **Implicit today.** Component shapes evolve in `api/src/main/java/infinity/es/...` as gameplay needs change. Some are wire-crossing (synced to client via Zay-ES `FieldSerializer`); changes to those fields must coordinate server + client + (future) modules. Coordination is currently negotiated per change, with no rule for "when can a component shape change without staged rollout?"
- **What an ADR would settle:**
  - Which component-shape changes are wire-breaking vs wire-compatible (add field with default? rename? reorder?).
  - Staged-rollout policy for breaking changes (deploy server first? client first? require version bump?).
  - How module-defined components ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) interact with the manifest-hash handshake when their shape changes.
  - Whether `Serializer.registerClass` registration order matters for cross-version compatibility.
- **Trigger to draft.** First time a wire-component shape needs to change after a client-vs-server version skew is realistic (e.g. dedicated-server deployments where the client is on a release tag and the server isn't). Or when modules ship and their component shapes need versioning.


## How items leave this backlog

- **Become a drafted ADR** when the trigger condition above is met.
- **Get folded into another ADR** if the implicit decision turns out to be covered by an adjacent area (e.g. *Persistence* might fold into [ADR-0004](../docs/adr/0004-settings-pipeline.md) if the durable-state model ends up `zone/`-driven; *Asset pipeline* probably folds the same way).
- **Get deleted** if the implicit decision is really a non-decision ("we don't do this" rather than "we do X").

## What this list is NOT

- **Not a TODO list.** Drafting an ADR without a real consumer is the same YAGNI trap that motivated the `modules/` subproject deletion. The series risks becoming theatre if entries land here just to look complete.
- **Not a survey of every implicit decision.** Many implicit decisions are fine implicit — language choice, code style, library minor versions. The list above is "implicit decisions someone might reasonably question," not "all decisions that exist."
- **Not architecturally urgent.** Infinity's existing ADR coverage is unusually thorough for a hobby-scale game project (7 ADRs is high-end). The backlog is wider-lens completeness, not a deficit.

## See also

- [`docs/adr/`](../docs/adr/) — landed ADRs (0001–0007).
- [`architectural-review-2026-05-13.md`](./architectural-review-2026-05-13.md) — actionable code-level findings; the prioritized P0 / P1 / P2 punch list.
- [Joel Parker Henderson's ADR examples repo](https://github.com/joelparkerhenderson/architecture-decision-record) — catalog of example ADR topics from other projects for comparison.
