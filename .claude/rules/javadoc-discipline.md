# Javadoc Discipline — keep it tight, cross-link don't duplicate

Javadoc is a *comment*. The CLAUDE.md global rule applies to it
unchanged: **"Default to writing no comments. Never write
multi-paragraph docstrings or multi-line comment blocks — one short
line max."** This rule restates the discipline for `*.java` files
specifically because agents tend to treat javadoc as different from
inline comments and over-document.

## What "one short line" means in practice

- **Records / data-holder classes** (`*Change`, `*Stats`, value
  objects, simple components): one sentence describing the WHAT,
  with a cross-reference to the canonical example or rule.
- **System / service classes** (`*System`, `*Service`, `*Logic`):
  one sentence describing what the system owns / drains / mutates.
  Plus one short line per non-trivial method where the *behavior*
  isn't obvious from the method name.
- **Public API contracts** (factories, interfaces in
  `infinity.sim.*`): the WHAT may need a second sentence if the
  contract has invariants. Keep both sentences short.

## The three-category test (load-bearing for a strict sweep)

Before deleting any javadoc paragraph or `//` comment, sort it into
one of three buckets:

1. **DELETE — pattern restatement.** Describes a recipe, rule, or
   convention that is already documented in a `.claude/rules/*.md`
   file, an ADR, or the rule's live snapshot. Restating it here
   means two copies that will drift. Cross-link with `@see` /
   `{@link}` instead.

2. **DELETE — WHAT-describing-the-code.** Paraphrases what
   well-named identifiers already say. "Returns the entity's energy
   pool" on a method named `getEnergyPool()` is paraphrase. Delete.

3. **KEEP — domain fact.** A specific, load-bearing piece of
   knowledge that is NOT in any rule file and CANNOT be derived by
   reading the code in isolation. Examples below.

When in doubt, **KEEP**. A future agent (or human) who has to
re-derive a domain fact by grepping commit messages or reading
external Subspace docs will pay the cost; the rule is "less
docstring" not "no docstring".

## Domain facts that MUST be preserved (with examples)

- **Subspace canon translations** — *"`MaximumRotation=400` means
  one full rotation per second; divide by 400 to convert to
  rad/sec"*. The `400` is a magic number until this comment names
  it. KEEP as a one-line `//` above the constant.
- **Unit conversions at boundaries** — *"Stats stores
  `fireDelayMillis` (ms); Subspace canon is `BombFireDelay`
  centiseconds. Conversion happens here."* KEEP at the conversion
  site.
- **Race-condition mitigations** — *"DefaultEntityData.removeEntity
  iterates handlers in non-deterministic order — cache
  (target, delta) at apply-time."* KEEP in the affected writer's
  Javadoc or at the TrackedApply field.
- **Ordering / lifecycle constraints** — *"Register before
  DecaySystem per ADR 0001 writer-ordering rule."* KEEP at the
  registration site or in the system's class Javadoc.
- **Attribution semantics** — *"`source` field carries the
  wormhole entity ID for wormhole-driven warps; the chat command
  variant passes the player's avatar ID."* KEEP at the emit site
  or in the wrapper type's Javadoc.
- **Architectural maps / "where things live" tables** — when a
  class is the orchestrator for a split (e.g. `ShipSpawnSystem`
  dispatching to `ShipStatusProjector` + `ShipWeaponsProjector`),
  a one-paragraph map of "which projector owns which aspect" is a
  navigation aid for future contributors. KEEP it in the
  orchestrator's class Javadoc.
- **REFERENCE.md cites for prize appliers** — per
  [`prize-applier.md`](./prize-applier.md), every applier class
  Javadoc must cite the relevant Subspace section. KEEP the cite.
- **Documented divergences from Subspace canon** — *"Infinity
  simplifies X; Subspace canonical behaviour is Y, see
  REFERENCE.md `## Section`."* KEEP at the divergence site.

If a comment is structurally one of the above but written
verbosely, **trim the prose, keep the fact**. "MaximumRotation=400
means one full rotation per second per the SubspaceServer source
at ClientSettingsConfig.cs:388, verified by Asser in 2024-..." →
"`MaximumRotation=400` = one rotation/sec (Subspace canon)."

## What does NOT belong in javadoc

- **Restated recipes** — if the four-line state machine, the
  Change-entity drain shape, the multi-source summing rule, the
  Decay-presence distinction, or any other pattern is documented in
  a `.claude/rules/*.md` file or in an ADR, **cross-link with
  `@see` / `{@link}` — do not restate.** Restatement drifts.
- **Worked code examples** — the test fixtures
  (`CanonicalWriterDrainTest`, `EnergySystemChangeDrainTest`, etc.)
  are the canonical examples. Pointing at them once is enough.
- **Generic class properties** — "this is server-only", "no
  serializer registration needed", "drained the same or next tick"
  are properties of the pattern, not of any individual class. They
  live in the rule, not in every `*Change` class.
- **Lifecycle discussions** — one-shot vs temporary, with-Decay vs
  without — are pattern-level. Don't restate per class.
- **Restating field types or names** — `@param delta the delta`
  adds no information; omit. `@param delta` is only worth writing
  when the param's meaning is non-obvious from its name and type.
- **History / migration notes** — "supersedes the pre-ADR
  `Intent + CapBump` route" is interesting in the PR description
  and the commit message; not in the type's permanent javadoc.

## Concrete shape — `*Change` record (the most over-documented type)

Acceptable:

```java
/** Additive delta to live {@link Thrust}; pairs with {@link ChangeTarget}. Drained by {@code ThrustSystem}. See ADR 0001. */
public record ThrustChange(int delta) implements EntityComponent {
  public ThrustChange() { this(0); }
}
```

Six lines of file content. One line of javadoc. Everything generic
about the Change-entity pattern lives in
[`replacement-as-mutation.md`](./replacement-as-mutation.md) and
[`docs/adr/0001-ecs-component-model.md`](../../docs/adr/0001-ecs-component-model.md).
Anything specific to Thrust as a *capability* lives in the
canonical writer system, not the payload record.

Unacceptable (real example, ~85 lines of javadoc for ~5 lines of
code): a class-level javadoc that explains the emit shape with a
worked `setComponents` snippet, the one-shot vs temporary
distinction, the rocket-buff clamp bypass, multi-source summing,
no-op skip, server-only-ness, and a migration history note. **Every
one of those bullets is generic to all `*Change` types** — they go
in the rule, not in every class.

## WHY-comments at the site

A genuinely non-obvious decision (e.g. "temporary deltas bypass the
clamp because rocket-buff overrides exceed `ThrustStats.max`") goes
as a **one-line `//` comment at the site** in the canonical writer
where the bypass is implemented — not in the payload record's
javadoc.

```java
// ThrustSystem.apply(...)
if (hasDecay) {
  // Bypass clamp: rocket-buff RocketThrust (e.g. 100) exceeds ThrustStats.max (e.g. 19).
  setThrust(target, current + delta);
} else {
  setThrust(target, Math.min(current + delta, stats.max()));
}
```

One line at the site. Not nine paragraphs in the payload.

## When you're tempted to add more

Ask: "would removing this javadoc confuse a future reader who already
has the rule + ADR + test fixture open?" If no, delete it. If yes,
trim it to one sentence and `@see` the canonical doc.

## Reference

- CLAUDE.md global rule — "Default to writing no comments. Never
  write multi-paragraph docstrings or multi-line comment blocks —
  one short line max."
- [`replacement-as-mutation.md`](./replacement-as-mutation.md) — the
  Change-entity recipe is here. Don't restate in class javadoc.
- [`docs/adr/0001-ecs-component-model.md`](../../docs/adr/0001-ecs-component-model.md)
  — the ADR. Cross-link, don't restate.
