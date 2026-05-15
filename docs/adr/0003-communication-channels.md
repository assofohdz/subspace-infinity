# ADR 0003 — Communication channels: intent components for mutation, EventBus for announcement

**Status:** Proposed
**Date:** 2026-05-13
**Deciders:** Asser Fahrenholz

## Context

Every cross-system communication in the server falls into one of two fundamentally different shapes, but both have been called "events" in the codebase at different times, and the overload has produced repeat questions in code review:

- *"Should this be a `Damage` component or a `DamageEvent` on the bus?"*
- *"Why doesn't `EnergySystem` listen for `ShipDestroyed` on the bus — it has to react?"*
- *"Can I publish from the client?"*

The CONTEXT.md "Events" section already names three planes (ECS transient component, arena `EventBus` event, zone `EventBus` event) with a worked example of when to pick which. CONTEXT.md's "Flagged ambiguities" section explicitly records "event" as a previously-overloaded term that was resolved by definition; this ADR moves that resolution out of vocabulary prose into a normative decision document and adds the runtime / scope clarifications that prose can't carry.

**Today's state of the codebase** (audited 2026-05-13):

- The first plane — "ECS transient component" — is the Change-entity mechanism already standardised by [ADR-0001](./0001-ecs-component-model.md). Every mutation request flows through `ChangeTarget + *Change` entity holders drained by canonical writers. ~95 component types audited, 0 multi-writer violations.
- The second and third planes — arena and zone `EventBus` events — share one `EventBus` instance at runtime. There are three publish call sites in the entire server / client codebase (`AccountEvent.playerLoggedOn`, `AccountEvent.playerLoggedOff`, `ShipEvent.shipSpawned`) and one client listener (`ClientEvent.clientConnected/clientDisconnected` in `MainMenuState`). `ShipEvent` retains only `shipSpawned`; the formerly-dormant types (`shipDestroyed`, `weaponFiring`, `weaponFired`, `shipChangeAllowed`, `shipChangeDenied`) and the `PlayerEvent` class (`playerBanned`) were deleted in P2-j.
- The "arena vs zone" split is a *naming convention* on the same bus instance — events declared under `infinity.events.arena.*` carry an entity ID payload from which a listener can derive `ArenaId`; events under `infinity.events.zone.*` are server-global by intent. Nothing at runtime enforces the split today.

**ECS-literature signal** (briefly, because the calculus is informed by it):

- Both channels coexist in mature ECS frameworks. Bevy provides `Event` queues + `MessageReader` for one-to-many broadcast; Flecs has observers for component lifecycle plus regular components for game-state mutation. Sander Mertens (Flecs) explicitly cautions against using components for everything, citing the one-tick lag problem when "events as components" are read by systems that already ran earlier in the tick. Bevy mitigates with a two-frame buffer.
- That one-tick lag is exactly what ADR-0001 calls out under Costs and explicitly *accepts*: "Next-tick visibility is intentional, not a defect." Infinity has chosen the component-tier shape for mutation requests with eyes open. The other shape — fire-and-forget broadcast — does not have the same lag tension because no consumer is mutating a target's authoritative state in response.

This ADR formalises the channel-shape decision so it stops being re-derived in code review.

## Decision

**The server has two general communication channels with non-overlapping responsibilities, plus a narrow allowance for domain-specific channels where the general primitives don't fit. Choose by the shape of the communication, not by the topic.**

### Channel A — intent components (Change entities)

Per [ADR-0001](./0001-ecs-component-model.md): a transient entity carrying `ChangeTarget(target, source)` + a typed `*Change` (or `*StatsChange`) payload. Created by any number of producers, drained by the one canonical writer for the paired component type.

**Use intent components when the communication is a mutation request:**

- A request to change a specific entity's authoritative state (`HealthChange`, `EnergyStatsChange`, `SpeedChange`, `FrequencyChange`).
- A request that must be *queryable* mid-tick — "is there pending damage on this ship?" answered by an `EntitySet`.
- A request that needs *framework atomicity* — applied at the Zay-ES flush boundary, no torn state visible to readers.
- A request that needs *per-entity scope baked into the data* — `ChangeTarget(target, …)` carries the target; the arena is derivable from the target's `ArenaId`.
- A request with *multi-source folding semantics* — multiple producers writing in the same tick stack additively (or with the documented per-aspect rule), and the canonical writer reconciles.

Intent components ride the ECS storage, are server-side only (clients see post-tick component state via Zay-ES / SimEthereal sync), and benefit from the one-writer-per-component discipline of ADR-0001.

### Channel B — Simsilica EventBus events

A static `EventBus.publish(eventType, event)` call delivers to all registered listeners synchronously and out-of-tick. Listeners register with `EventBus.addListener(this, eventType)` and remove with `removeListener` in their lifecycle. No queueing, no replay, no scoping by entity ID (any scoping is read from the event payload by the listener).

**Use EventBus when the communication is an announcement:**

- "X just happened" — login, logout, ship spawned, ship destroyed, arena loaded, weapon fired. A factual statement about a state transition that already occurred.
- *One-to-many fan-out* with disjoint consumers — HUD, scoreboard, audio, replay logger, lobby UI, master-server pings. The producer doesn't know or care who's listening.
- *No queryability requirement* — nobody walks an `EntitySet` of past events; consumers react in their listener and forget.
- *No atomic-apply requirement* — listeners run synchronously when published; if a listener fails, the failure doesn't roll back others.
- *Cross-cutting* concerns — telemetry, logging, account services, UI lifecycle — that should not be load-bearing for game-state correctness.

EventBus events are server-side only at runtime (the `EventBus` static singleton lives inside the JVM); they do not cross the network directly. For informational announcements that the client must also observe, `EventBusBroadcastHostedService` (server) + `EventBusBroadcastClientService` (client) bridge selected events over RMI callbacks: the server subscribes to the curated EventTypes, fans out via per-connection RMI calls, and the client republishes onto its own local-only EventBus. The client-side bus is a local notification bus; it does not feed back to the server (per ADR-0005).

### Scope tiers inside Channel B

EventBus events are further classified by scope:

- **Zone event** — server-global. Login, logout, master-server status, arena-create / arena-destroy, account state. Lives under `infinity.events.zone.*` (or alongside its publisher when the event is session-coupled, e.g. `infinity.net.AccountEvent`). Listeners are server-level (account services, lobby UI, telemetry).
- **Arena event** — scoped to a single arena. Ship lifecycle, weapon firing, future flag / score / KOTH events. Lives under `infinity.events.arena.*` (e.g. `ShipEvent`). The event payload carries an `EntityId` from which the listener derives `ArenaId` for filtering.

**Today the same `EventBus` instance carries both tiers.** Zone listeners simply do not register for arena event types; arena listeners that span multiple arenas filter by `ArenaId` themselves. The split is enforced by the type system (event types declared in the matching package) and by listener discipline, not by separate runtime infrastructure.

**Splitting into per-arena bus instances is deferred** until a concrete consumer demands it. The trigger condition: a listener that should only see events from one arena starts seeing events from another and the listener-side filter is not enough (e.g. a HUD in arena B observes arena A's events because both subscribe to the same type, and the cost of filtering N×M cases becomes structural). Until that scenario is real, the static `EventBus` + payload-derived `ArenaId` is the right shape.

### Channel C — domain-specific channels (narrow allowance)

Some communication does not fit Channel A or Channel B. The canonical example is **`ContactSystem`**: physics contacts fire at physics-tick rate with potentially many events per tick, need custom filtering (sensor / category / parent-child) before fan-out, and have listener-order dependencies that matter for correctness (e.g. `WeaponsImpactSystem` must process a projectile-vs-ship contact before `PrizeConsumptionSystem` so kill-credit is recorded before any prize-consumption side-effects). Seven systems register against `ContactSystem` today.

`ContactSystem`-shape channels are neither intent components nor `EventBus` events. They are *domain-specific listener channels* owned by one canonical dispatcher.

- **Not Channel A** — they are domain notifications, not mutation requests. The consumers are not asking the dispatcher to mutate a target; the dispatcher is reporting "a thing happened in my domain, here are the details, react if you care". Per-contact entity churn would be expensive at physics rates, and consumers do not need queryability or framework atomicity; they need synchronous fan-out with custom filtering.
- **Not Channel B** — physics-tick rate is too high for `EventBus`'s broadcast-everything-typed semantics, custom pre-dispatch filtering is required (the consumer registration must include the filter to avoid every listener seeing every contact), and listener ordering must be controllable (`EventBus` does not guarantee or expose ordering).

**Bar for adding a new domain-specific channel.** New Channel C channels are an *exception*, not a default. The discipline is:

1. The general primitives demonstrably don't fit — typically: very high frequency, custom pre-dispatch filtering, or listener-order requirements.
2. The channel is contained to a specific domain (physics, AI, sound) — not a general communication channel that arbitrary systems use casually.
3. One canonical owner system controls registration and dispatch. There is one place to grep for "who listens to X".
4. The listener contract (ordering guarantees, filter semantics, dispatch timing) is documented in the dispatcher's class Javadoc.

If those four hold and the general primitives would force structural compromise (entity churn at firehose rate; broadcast-typed dispatch that has to be re-filtered by every listener), a Channel C channel is justified. Otherwise prefer Channel A or B.

### Decision tree

```
Is the communication a request to mutate authoritative state?
  ├─ Yes → intent component (Channel A; see ADR-0001 for the recipe)
  └─ No → it is an announcement or a domain notification
       Is it a high-frequency domain firehose with custom filtering or
       listener-order requirements that EventBus can't natively provide?
         ├─ Yes → domain-specific channel (Channel C)
         │        Apply the four-point bar before adding a new one;
         │        prefer using or extending an existing dispatcher.
         └─ No → general announcement
              Does only the server need to know?
                ├─ Yes → EventBus (Channel B)
                │        Is the event meaningful outside one arena?
                │          ├─ Yes → zone event (infinity.events.zone.*)
                │          └─ No  → arena event (infinity.events.arena.*)
                └─ No, the client needs to react too
                     → not a bus event on the shared server bus. Choose:
                       · the resulting state is a component the client
                         already observes via SimEthereal — react there;
                       · the trigger is a client request — use RMI
                         (see GameSession.java); RMI is the wire-crossing
                         channel, not the bus;
                       · informational announcement, no shared-state
                         write — bridge via EventBusBroadcastHostedService
                         (RMI callback → client republishes onto its own
                         local EventBus; see ADR-0005 client-read-only
                         constraint). Current: PlayerKilledEvent.
```

### Naming hygiene

- `*Change` / `*StatsChange` are intent-component types. They live in `api/src/main/java/infinity/es/...` and implement `EntityComponent`. They are not called "events" anywhere in code or commit messages.
- `*Event` is reserved for `EventBus` events. They live in `api/src/main/java/infinity/events/{arena,zone}/` (or alongside the publisher for session-coupled events).
- "Marker components" (`Repellable`, `Captain`, `ResetLivePool`) are state markers, not events. They persist; they are not transient.
- "Transient component" as a term is retired in favour of "intent component" or "Change entity" — both ADR-0001's vocabulary. Using "event" for these caused the original ambiguity.

### Relationship to ADR-0001

[ADR-0001](./0001-ecs-component-model.md) standardises Channel A (the mutation-request shape). This ADR codifies that Channel B is a separate, non-overlapping primitive.

Common mistake the two ADRs together prevent: publishing a "damage event" on the bus so other systems can react. The right shape is a `HealthChange` intent component (ADR-0001) drained by `EnergySystem`, with reactors observing the `Health` component change via `EntitySet.getChangedEntities()` (ADR-0001 phase 3). The bus event would be redundant and racy.

## Consequences

### Positive

- **One primitive per shape, no overloading.** "Is this a mutation request or an announcement?" has one answer; that answer picks the channel.
- **Queryability and atomicity stay where they belong.** Intent components ride ECS grain; their atomicity comes for free from Zay-ES `applyChanges()`. Bus events are explicitly fire-and-forget — no spurious queryability expectations.
- **The one-tick lag has a home.** It's a property of intent components (per ADR-0001's accepted cost) and not of bus events. Code review can flag "you're using a bus event because you don't want the one-tick lag" — the right fix is usually to react to the component change directly, not to bypass the discipline.
- **Bus surface is small and stays small.** The audit found three live publish sites. The naming hygiene rule keeps it that way: things that look like mutations are not allowed to enter the bus.
- **Per-arena scope is encoded twice over** (event-type-by-package + `ArenaId` in payload). Listener bugs ("I'm seeing events from another arena") are findable by reading the payload, not by debugging runtime bus topology.
- **Network surface stays unambiguous.** The server-side `EventBus` does not cross the wire directly. Clients use SimEthereal for state observation and RMI for intent submission. Informational server events reach the client via the `EventBusBroadcast` RMI bridge (one curated channel, read-only on the client side).

### Costs (accepted, not avoided)

- **Two channels means two mental models.** New contributors have to learn both. Mitigation: the decision tree above, plus the convention that the two channels never overlap in role.
- **The arena-vs-zone naming convention is enforced by discipline, not runtime.** A misclassified event type (declared under `arena.*` but actually zone-scoped, or vice versa) will compile and run; the bug surfaces only if a listener over-fires. Mitigation: code review against package placement; per-arena bus split available as a future runtime guard if the discipline fails.
- **Dead event types accumulate.** Dormant `EventType` constants were culled in P2-j; `ShipEvent` now declares only `shipSpawned`. Declaring an `EventType` for a planned-but-not-yet-published case signals intent that may not survive — cull proactively.
- **Bus events are not durable.** A subscriber that boots after a publish never sees the event. Acceptable for the current consumer set (HUD, audio, telemetry — all alive for the session); not acceptable if a future consumer needs replay. That consumer is responsible for either persisting state itself or moving to an intent-component shape if queryability is needed.

### Neutral / deferred

- **Per-arena `EventBus` instances** are deferred. Adding them requires either a `Map<ArenaId, EventBus>` lookup at publish time, an `EventBus` field on the arena entity, or a per-arena `GameSystemManager`-scoped bus. The choice depends on the consumer that triggers the split; making the choice now is premature.
- **Bevy-style buffered queues with multi-frame retention** are not adopted. Infinity's intent-component channel already accepts the one-tick lag explicitly. The bus channel does not need replay because no consumer requires it today. If a consumer ever does (e.g. a deferred reactor that needs to see the last N spawns), it can buffer locally rather than the bus channel growing complexity.
- **Cross-arena announcements** ("a tournament event spans arenas A, B, C") have no current consumer. If they appear, the natural fit is a zone event, not an arena event multicast to several arenas.

## Alternatives considered

- **Unify under EventBus.** Rejected — loses framework atomicity and the one-writer-per-component discipline of ADR-0001.
- **Unify under intent components.** Rejected — bus events have no target entity; entity churn for cross-cutting broadcasts costs more than it buys.
- **Per-arena `EventBus` instances now.** Rejected as premature; trigger condition documented above.
- **Bevy-style buffered events with two-frame retention.** Rejected — Channel A already accepts one-tick lag; Channel B has no replay consumer today.
- **Flecs-style observers on component add / remove as Channel B.** Rejected — Zay-ES `EntitySet.getChangedEntities()` already handles reactor logic at ADR-0001 phase 3.
- **Subsume `ContactSystem` under Channel A or B.** Rejected — per-contact entity churn at physics rates is expensive; `EventBus` cannot natively provide custom pre-dispatch filtering or listener ordering.
- **Keep the "three planes" framing.** Rejected — "two general channels + a narrow Channel C allowance" is normatively accurate; the three-plane prose stays useful in CONTEXT.md but is not the ADR-level shape.

## Resolved decisions

- **Three channels by shape, not topic:** A = intent components (mutation requests, per ADR-0001); B = EventBus (announcements); C = domain firehoses (narrow allowance; current: `ContactSystem`).
- **Channel B scope:** zone events server-global, arena events arena-scoped (payload-derived `ArenaId`); same `EventBus` instance today; per-arena split deferred.
- **Channel C bar:** general primitives don't fit; one domain; one canonical owner; listener contract documented in dispatcher's Javadoc. Default is *don't add one*.
- **Naming:** `*Change` / `*StatsChange` for intent components; `*Event` for bus events; "transient component" retired.
- **Network:** Channel B (server `EventBus`) is server-only. Clients observe component state via SimEthereal; submit intent via RMI. Informational server-side events can be bridged to the client's local-only EventBus via `EventBusBroadcastHostedService` + `EventBusBroadcastClientService` (RMI callback fan-out; no shared-state writes through this channel — ADR-0005).

## Open work

The decision is fully described above. What remains is enforcement and cleanup, tracked in the architectural review punch list rather than here:

- **Audit dormant `EventType` declarations.** Culled in P2-j; verify no new dormant types accumulate.
- **Define a trigger condition for per-arena bus instances** if the cross-arena-leak scenario becomes concrete.
- **Cross-link rule files.** `.claude/rules/` does not currently have an `events.md` rule covering Channel B; consider adding one or rolling the discipline into a CONTEXT.md cross-reference from this ADR.
- **Sweep CLAUDE.md / CONTEXT.md / `.scratch/`** for "three planes" framing once this ADR lands. Update CONTEXT.md's "Events" section to reference this ADR rather than restating.

## References

- [`docs/adr/0001-ecs-component-model.md`](./0001-ecs-component-model.md) — defines the intent-component (Change entity) channel that this ADR codifies as Channel A.
- [`.claude/rules/replacement-as-mutation.md`](../../.claude/rules/replacement-as-mutation.md) — the operational recipe for Channel A; ADR-0001 supersedes its scope but RaM remains the per-component-write rule.
- [`CONTEXT.md`](../../CONTEXT.md) "Events" section — the three-plane teaching this ADR refines. Update CONTEXT.md to cross-reference once this ADR is Accepted.
- `api/src/main/java/infinity/events/arena/ShipEvent.java` (`shipSpawned` only), `api/src/main/java/infinity/net/AccountEvent.java` — current `EventType` surface. `PlayerEvent` was deleted in P2-j.
- Sander Mertens, "Building an ECS" series — discusses both event-as-component (with the one-tick-lag tradeoff) and observer/bus patterns; informs the rejection of unifying under one channel.
- Bevy `Event` / `MessageReader` documentation — alternative buffered-queue design considered and rejected for this codebase.
- Simsilica `sim-event` (the `EventBus` library) — the Channel B implementation.
