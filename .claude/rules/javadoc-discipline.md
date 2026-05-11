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
