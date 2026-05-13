# ADR 0006 — Tuning knobs vs. magic numbers: the literal-promotion decision

**Status:** Proposed
**Date:** 2026-05-13
**Deciders:** Asser Fahrenholz

## Context

[ADR-0004](./0004-settings-pipeline.md) decided *how* tuning travels (the Groovy → typed `*Config` → component pipeline) and *where* it lives (the `zone/` tier hierarchy). This ADR answers a different question that [ADR-0004](./0004-settings-pipeline.md) does not: **for any given literal value in the codebase, does it belong in Groovy or in Java?**

The question matters because the costs of the two answers are *asymmetric*:

- A literal that should be a tuning knob, sitting in Java, is **invisible to operators**. Only contributors can change it; every change is a rebuild; balancing the game becomes a code-review pipeline. Worst case: the operator never even knows the knob exists.
- A literal that should be a true constant, sitting in Groovy, costs a little ceremony (a `*Config` record field, a projector line, a Groovy entry) but is mostly harmless. The settings pipeline absorbs it.

Game code accumulates literals fast. Energy regen rates, fire delays, splash radii, drag coefficients, ship handling feel, prize spawn cadence, friendly-fire toggles, max-bombs caps. Every PR review has at least one moment where a contributor types `60` or `1000` or `0.99` and someone has to decide. CLAUDE.md Rule 3 is the operational policy already — this ADR records the decision shape behind it so the rule does not have to argue its own case every time.

The broader pattern has a name in the game-development literature: **data-driven design**, the practice of externalising behaviour-controlling values from compiled code into data files that operators (and game designers) can edit independently. Subspace Infinity's settings pipeline ([ADR-0004](./0004-settings-pipeline.md)) is the data-driven design substrate; this ADR is the policy that decides what data flows through it. The complementary discipline from general software engineering — Fowler's *Replace Magic Number with Symbolic Constant* refactoring — is what governs the values that *stay* in Java. The two disciplines cover the same axis from opposite ends; this ADR places the boundary between them.

## Decision

**Lean toward Groovy by default. Java literals stay only when they are true magic numbers: math identities, well-known protocol constants, or implementation-detail loop bounds.** [ADR-0004](./0004-settings-pipeline.md) handles the *where* and *how*; this ADR is the *whether*.

### The smell test

Promote to Groovy when the literal *smells like a tuning knob* — any one of:

- **Gameplay balance.** Damage values, fire delays, prize weights, energy capacities, score thresholds, weapon levels, drop rates.
- **Physics feel.** Drag coefficients, restitution, turn responsiveness, thrust ramp, collision radii (when scaled by ship type).
- **Timing budget.** Tick rates, poll intervals, retry windows, decay durations, jitter envelopes.
- **Threshold.** Any value where the gameplay answer to "what if it were 20% higher / lower?" is interesting.

Stay in Java when the literal is a *true magic number* — narrowly:

- **Math identities.** `2π`, `1/√2`, `π/180`, integer-byte conversion constants. These are properties of mathematics, not gameplay.
- **Well-known protocol constants.** Network-frame headers, file-format magic bytes, version bytes. Changing them changes the protocol; that's not a tuning decision, that's a breaking change.
- **Loop bounds and implementation details.** `MAX_INCLUDE_DEPTH = 16` in the Groovy fragment loader, array sizes that exactly match a fixed algorithm. Not gameplay-visible; changing them is an implementation refactor, not a tuning move.
- **Sentinel / disabled values.** `-1` to mean "not set", `0` to mean "feature off". These ARE values, but they're API contract markers, not balance knobs.

When **unsure**, lean Groovy. Demoting a Groovy knob back to a Java constant is a one-line change. Flushing a Java magic number out of compiled code requires a `*Config` field addition, a projector line, an adapter parameter, and a settings-pipeline tracker row — strictly more friction. Pick the cheap-to-undo direction by default.

### Scope discriminator (which tier?)

[ADR-0004's tier hierarchy](./0004-settings-pipeline.md) names *engine / zone / arena / per-arena-typed-fragment*. The decision of *which tier* a given knob lives in is not "what file is closest" but "what's the right scope":

| Question | Tier | Example |
|---|---|---|
| Would two arenas in the same zone ever want different values? | per-arena typed fragment or arena.groovy | `BombDamageLevel`, `ShipStats` per `ShipType` |
| Is this a zone-wide policy that arenas inherit? | `zone.groovy` | Default chat rate-limit, default access role |
| Is this a *physics or engine constant* that no operator would ever want different per arena? | `engine.groovy` | Collision radii, unit scales, physics integration clamps, gravity-cell size |
| Is this a tuning template shared across arenas of the same style? | preset (`zone/conf/<preset>/`) | `trench-04-2026/bullet.groovy`, ship stat templates |

**The dispositive test for engine vs. per-arena: ask "would two arenas in the same zone ever want different values?"** If yes → per-arena. If no → engine. Physics constants almost always answer no; gameplay constants almost always answer yes. Misclassifying a physics constant as per-arena adds a knob nobody wants to tune; misclassifying a balance constant as engine forces a server restart to change something operators reasonably tune per arena.

### Decision tree

```
Found a literal in Java code (or about to type one).
   │
   ▼
Is it a math identity, protocol constant, loop bound, or sentinel?
   ├─ Yes → keep in Java (with a one-line `//` comment naming WHY it's a magic number).
   └─ No  → it's a tuning knob.
        │
        ▼
        Would two arenas in the same zone ever want different values?
          ├─ No  → engine.groovy (zone-wide constant)
          └─ Yes → per-arena typed fragment (zone/conf/<preset>/*.groovy) or
                   arena.groovy (per-arena override).
                   Choose preset when the value belongs to a balance template
                   that other arenas of the same style share. Choose arena.groovy
                   when it's a per-arena override of a preset value.
```

## Consequences

### Positive

- **Operator-editable by default.** Every gameplay-visible knob is one Groovy edit away from a tuning round, no rebuild required.
- **Asymmetric-cost discipline.** The "lean Groovy when unsure" default puts friction in the right place: knobs need a Groovy line to exist; true constants stay where they are with no ceremony.
- **Clear scope-decision algorithm.** "Would two arenas want different values?" is one cognitive check, settles the engine-vs-per-arena question without case-by-case judgment.
- **PR-review predictability.** A literal in a Java diff is a code-review prompt: "is this a tuning knob?" The contributor either justifies the Java placement (cite the magic-number list) or moves it to Groovy.
- **Inventory exists.** `.scratch/settings-pipeline.md` tracks per-key Groovy migration status; the asymmetric-cost rule is what drives entries onto that tracker rather than into Java.

### Costs

- **Two-tier maintenance.** Every new Groovy field touches a record, a projector, an adapter, and the tracker. Worth it for tuning knobs; overkill for true constants. The exemption list keeps the cost proportional to value.
- **Judgment calls don't disappear.** "Is this radius a tuning knob or a physics constant?" sometimes lands genuinely ambiguous; resolve in PR review against the questions above. Bias to Groovy on tie.
- **Per-arena vs. engine misclassification is real.** A `*Config` field that no one ever changes is dead weight; a Java constant that operators wanted to tune is a missed tuning loop. Memory note from prior incidents: *physics constants → `engine.groovy`*, *gameplay → per-arena*; that informs the scope test above.

### Neutral / deferred

- **Mechanised enforcement** — a static check that flags Java numeric literals outside an allowlist (math constants, sentinels) is conceivable but high-noise. Code-review discipline plus the settings-pipeline tracker is enough for now. Revisit if drift becomes a recurring problem.
- **Sentinel-value boundary** with the magic-number list is fuzzy. `-1` for "not set" is canonical; but `Status == 0/1/2` tri-state values per Subspace canon are *both* gameplay (per [REFERENCE.md](../../.scratch/subspace-ini-reference/REFERENCE.md)) and protocol-shaped. Resolve case-by-case against the smell test — Subspace tri-states are gameplay, sentinel `-1` is protocol.

## Alternatives considered

- **All literals stay in Java; expose them via reflection / annotation-scanned admin tool.** Rejected — loses operator-editable workflow; requires a custom tool; no live-reload; defeats [ADR-0004](./0004-settings-pipeline.md)'s motivation.
- **All literals go in Groovy; no exemption list.** Rejected — promotes math identities and protocol constants to operator-tunable surface, which both confuses operators (these are not gameplay) and risks the operator nudging a constant into a state that breaks the protocol.
- **Database-backed config.** Rejected — heavy infrastructure for what `zone/` files already do well; loses version-control of tuning history; loses dev-mode filesystem-first workflow.
- **Compile-time codegen** that bakes Groovy values into Java constants at build time. Rejected — loses hot-reload (the primary tuning loop); reintroduces the rebuild cycle the pipeline exists to avoid.

## Resolved decisions

- **Default: lean Groovy.** When unsure, the literal goes in Groovy. Demote later if it turns out to be a true constant; that's the cheap direction.
- **Java exemptions:** math identities, well-known protocol constants, loop bounds, sentinel values. One-line `//` comment at the site naming WHY it's exempt.
- **Scope test:** "would two arenas in the same zone ever want different values?" — yes → per-arena; no → engine.
- **Where the knob lives** is [ADR-0004's](./0004-settings-pipeline.md) territory; *whether* it's a knob at all is this ADR.

## Open work

- The settings-pipeline tracker (`.scratch/settings-pipeline.md`) is the per-key inventory of Java-literal → Groovy migrations; this ADR is the policy, the tracker is the work queue.
- Mechanised lint for Java numeric literals (`if` rules + exemption allowlist) — deferred; revisit if review-time enforcement starts missing cases.

## References

- [`docs/adr/0004-settings-pipeline.md`](./0004-settings-pipeline.md) — the pipeline this ADR feeds into; *where* and *how* knobs travel.
- [`CLAUDE.md`](../../CLAUDE.md) Rule 3 — operational policy this ADR formalises.
- [`.claude/rules/settings-pipeline.md`](../../.claude/rules/settings-pipeline.md) — the per-key lookup rule (REFERENCE.md cite before authoring an adapter / `*Config` / consumer).
- [`.scratch/settings-pipeline.md`](../../.scratch/settings-pipeline.md) — per-key migration tracker; gets a new row each time a Java literal is promoted.
- [`.scratch/subspace-ini-reference/REFERENCE.md`](../../.scratch/subspace-ini-reference/REFERENCE.md) — Subspace canon for tuning-knob semantics + units.
- Martin Fowler, "[Replace Magic Number with Symbolic Constant](https://refactoring.guru/replace-magic-number-with-symbolic-constant)" — the canonical refactoring catalog entry for the Java-stays-Java path (math identities, protocol constants, well-known invariants).
- "[Data-Driven Design](https://dev.to/methodox/data-driven-design-leveraging-lessons-from-game-development-in-everyday-software-5512)" — broader game-development literature on externalising behaviour-controlling values; the umbrella for what this ADR enables.
