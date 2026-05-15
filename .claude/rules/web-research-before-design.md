# Web Research Before Design + Implementation

Before starting a non-trivial design or implementation effort, do a
short pass of **current** web research. Training data ages and project
memory ages even faster. A 60-second `WebSearch` / `WebFetch` lookup
catches recent library changes, deprecated APIs, and current best
practices that intuition or recall will miss.

## When this rule fires

Trigger the lookup before you commit to an approach when any of the
following are true:

- Integrating a **library, framework, or external API** (JME, Moss,
  Simsilica, jMonkey ecosystem, anything on Maven Central, REST APIs,
  protocol specs).
- Choosing between **algorithmic / architectural options** where the
  field has moved in the last year or two (concurrency primitives,
  serialization formats, build tools, CI patterns, AI/ML libs).
- Adopting a **new tool or CLI** (a Gradle plugin, a code-gen tool, a
  linter, a profiler) where the docs and defaults change between
  releases.
- Implementing against an **evolving spec or standard** (HTTP semantics,
  WebSocket, OAuth, a game-protocol reverse-engineering target like
  Subspace/Continuum).
- A user request mentions a **specific version or feature** ("use the
  new X in version Y") — verify it exists and is stable before designing
  around it.

The rule does **not** fire for:

- Pure local refactors, bug fixes inside this codebase, or renames.
- Edits to files where the surrounding code is the spec (ECS systems,
  Groovy fragments).
- Trivial Java / Groovy / Gradle questions you can answer by reading
  the file in front of you.

When in doubt, **do the lookup** — it's cheap. Especially if the task
involves a dependency listed in [`build.gradle`](../../build.gradle)
or any of the Simsilica / Moss / jMonkey libraries.

## How to apply

1. **State what you're about to check** in one sentence before the
   tool call ("Checking current jME 3.7 BaseAppState lifecycle docs").
   This makes the cost visible and lets the user redirect.
2. **One or two targeted queries, not a research project.** Prefer
   official docs (project site, GitHub README, release notes,
   spec/RFC) over blog posts. `WebFetch` an exact URL when you know
   it; `WebSearch` when you don't.
3. **Look for the version delta** — when was the doc last updated? Is
   the API you remember still the recommended one? Are there
   migration notes for the version this repo pins?
4. **Cite the source** in your design discussion. A bare claim
   ("Lemur uses X") with a link to the page that says so is much more
   useful than the claim alone, because the user can verify.
5. **Skip and say so** when the rule clearly doesn't apply — explicit
   "no external research needed because <reason>" beats silent skip
   the user has to infer from your behaviour.

## What NOT to do

- Don't skip research because "I know this library" — the memory rule
  *"verify before recommending"* applies double for external libraries.
  An API you remember from training may have been deprecated.
- Don't research **after** you've already drafted the design or
  written the code. The whole point is to inform the approach, not
  to validate after the fact.
- Don't burn 10 minutes researching a 30-second decision. Stop at
  one or two queries unless the user asked for a deep dive.
- Don't paste large doc dumps into the conversation. Summarize the
  fact you needed and link the source.

## Why

Two failure modes this prevents, both observed in past sessions:

1. **Stale-API designs.** Designing against an API shape that was
   refactored in a recent release, then having to redesign mid-
   implementation when the compile fails or the runtime behaves
   differently than expected.
2. **Reinventing the supported feature.** Implementing a custom
   solution for something the library now provides out of the box,
   because the "this isn't supported" memory predates the feature
   landing upstream.

Both are recoverable but expensive. A 60-second lookup at the
*design* stage prevents the rework.

## Reference

- `WebSearch` and `WebFetch` are the tools the harness exposes for
  this. They're deferred — load with `ToolSearch` first if needed.
- The CLAUDE.md memory rule *"Before recommending from memory:
  verify"* is the in-conversation analogue of this rule for memory
  entries; this rule extends the same discipline to external sources.
