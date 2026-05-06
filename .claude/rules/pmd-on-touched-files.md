# PMD — Run on touched files + ratchet one easy fix per batch

After completing any code change to `*.java` files, run PMD against the
touched files using the project's `pmdPath` task, surface the violations,
and **fix the single violation with the lowest remediation effort** as
part of the same work batch.

This is the project's ratchet mechanism: every code change is a tiny
opportunity to chip away at the existing 600-violation baseline (per
[`config/pmd/ruleset.xml`](../../config/pmd/ruleset.xml)) without ever
blocking real feature work. Over many batches the baseline trends down
without dedicated cleanup slices.

## How to apply

1. **Run PMD.** Group touched paths by their owning Gradle module
   (`api` / `infinity` / `modules`) — `pmdPath` is per-module and the
   path is relative to the module root.
   ```bash
   ./gradlew :<module>:pmdPath -PpmdPath=<comma,separated,relative,paths>
   ```
   Multiple files: comma-separate with no spaces; quote the
   `-PpmdPath=...` arg if it contains commas (Gradle property parsing).
2. **Surface violations.** Distinguish "violations introduced by this
   change" from "pre-existing violations PMD now sees because I touched
   this file." Both are visible in the same output; both are fair game
   for the fix step.
3. **Pick the lowest-effort violation and fix it.** See the effort
   tiers below. **Only fix one per batch** — the discipline is "always
   ratchet, never refactor under cover of feature work." If every
   visible violation is medium or high effort, skip the fix step and
   say so in the report.
4. **Re-run PMD on the touched files** after the fix to confirm the
   violation count decreased and no new ones appeared.
5. **Skip the whole flow** when no `*.java` files were edited (Groovy
   DSL fragments, `.scratch/*.md` trackers, build files, etc.).

## Effort tiers

Judgment-based — PMD doesn't classify rules by remediation effort
natively.

- **Low — fix as part of the batch.** One-line, mechanical, no
  behaviour risk:
  - `LooseCoupling` — `HashSet foo` → `Set foo` (only when not
    intentionally using impl-specific API).
  - `AppendCharacterWithChar` — `sb.append("x")` → `sb.append('x')`.
  - `UseIndexOfChar` — `s.indexOf(":")` → `s.indexOf(':')`.
  - `UselessStringValueOf`, `StringInstantiation`, `StringToString`,
    `AddEmptyString` — drop a redundant wrap.
  - `UnnecessaryImport`, `UnusedLocalVariable`, `UnusedAssignment`,
    `UnusedPrivateField`, `UnusedFormalParameter`, `UnusedPrivateMethod`
    — delete the dead code.
  - `OptimizableToArrayCall` — `c.toArray(new T[0])` swap.
  - `EmptyControlStatement`, `EmptyCatchBlock` — collapse / log.
- **Medium — fix only if the user asked.** Behaviour-adjacent or
  needs care:
  - `AvoidThrowingRawExceptionTypes`,
    `AvoidThrowingNullPointerException`,
    `SignatureDeclareThrowsException` — replace with a specific
    exception type; needs domain judgment.
  - `AvoidReassigningParameters` — introduce a local copy; might
    affect readability.
  - `SingularField` — promote to local; could conflict with future
    reuse if the field is meant to grow.
  - `CompareObjectsWithEquals` — `==` could be intentional identity
    check; verify before changing to `.equals()`.
  - `ConstructorCallsOverridableMethod` — needs careful restructure
    or `final` on the method.
  - `NonExhaustiveSwitch` — add `default` branch with appropriate
    behaviour; `throw new IllegalStateException(...)` is the safe
    default.
- **High — defer unless the slice's stated work is the refactor.**
  Refactor-scale:
  - `CyclomaticComplexity` / `CognitiveComplexity` / `NPathComplexity`
    — usually requires method extraction or control-flow rework.
  - `GuardLogStatement` — often false-positive for SLF4J `{}`
    placeholders; sometimes legit. Per-line audit needed.
  - `GodClass`, `TooManyFields`, `TooManyMethods` — class-level
    refactor.

## What NOT to do

- Don't run `pmdMain` across the whole module — too slow, too noisy
  for the "did I add anything" question. Reserve that for the
  occasional baseline ratchet pass.
- Don't fail the task on violations — `ignoreFailures = true` in
  [`infinity.java-conventions.gradle`](../../buildSrc/src/main/groovy/infinity.java-conventions.gradle)
  is intentional; warnings only.
- Don't fix more than one violation per batch unless the user asked
  for cleanup. Scope creep risk.
- Don't fix violations on files you didn't already touch in the
  batch — the rule is "ratchet on touched files," not "wander the
  codebase."
- Don't apply a Medium- or High-tier fix without explicit user buy-in
  (these can change behaviour or need a tests rerun for confidence).

## Why

Running `pmdMain` across the whole module on every iteration drowns
the signal — 600+ baseline violations make it impossible to tell "did
*my* change add anything." `pmdPath` scopes the report to the files
the change actually touched, which makes the per-change signal usable.

The "fix one easy violation per batch" rule turns every code change
into a tiny ratchet — over hundreds of batches the baseline trends
down without ever needing a dedicated "clean up tech debt" slice that
competes with feature work for time.

Pairing the ratchet with **strict scope-of-touched-files** keeps the
rule from blooming into incidental refactoring of unrelated code,
which would defeat the per-change signal it's protecting.

## Reference

- [`config/pmd/ruleset.xml`](../../config/pmd/ruleset.xml) — the
  active rule set (current Tier 1 + complexity + perf rules).
- [`buildSrc/src/main/groovy/infinity.java-conventions.gradle`](../../buildSrc/src/main/groovy/infinity.java-conventions.gradle)
  — `pmd { … }` config + `pmdPath` task definition.
- [PMD 7.x rule reference](https://docs.pmd-code.org/pmd-doc-7.7.0/pmd_rules_java.html)
  — full catalog if you need to look up a flagged rule's docs.
