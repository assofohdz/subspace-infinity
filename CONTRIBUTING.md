# Contributing to Subspace Infinity

Subspace Infinity is a single-maintainer, pre-alpha hobby project. Contributions are welcome — code, documentation, bug reports, balance feedback — and are reviewed on a best-effort basis. Reviews typically happen within 2–3 days; if your PR sits longer than that, ping in [Discord](https://discord.gg/FXqNB6N).

By submitting a pull request, you agree your contribution is licensed under the [BSD-3-Clause license](LICENSE.md) that covers the rest of the project. Note that `LICENSE.md` covers code and Subspace Infinity's own assets only — see [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) for material with separate provenance (Subspace/Continuum game files, community maps, attributed textures).

## Before You Start

This document covers **process and conventions** — how to file an issue, how to scope a PR, what rules your code must follow, and what verification a PR needs before merge.

It does **not** cover setup, build commands, or architecture. For those:

| You want to… | Read |
|---|---|
| Set up your dev environment | [docs/setup-guide.md](docs/setup-guide.md) |
| Build and run the project | [BUILDING.md](BUILDING.md) |
| Understand the codebase | [docs/developer-guide.md](docs/developer-guide.md) |
| Look up a common command | [docs/quick-reference.md](docs/quick-reference.md) |
| Learn the project's domain language | [CONTEXT.md](CONTEXT.md) |
| Read the canonical project rules | [CLAUDE.md](CLAUDE.md) |

## Filing an Issue

Issues live on the [GitHub issues page](https://github.com/assofohdz/Subspace-Infinity/issues).

**Before filing:**

- Check for duplicates.
- One issue per bug or feature.
- Stay on topic — bugs, features, and balance feedback only. General questions belong in Discord.

**For feature requests:** the gate is *"does it make the game more fun?"* This is a game, not a simulator. Changes that add complexity for the sake of realism won't be accepted.

### Labels

| Kind | Meaning |
|---|---|
| `bug` | Game not behaving as intended |
| `enhancement` | New engine or gameplay functionality |
| `docs` | Documentation improvements |
| `balance` | Gameplay balance issues |
| `mechanics` | Game mechanics changes |
| `content` | New content (no code changes) |

| State | Meaning |
|---|---|
| `needs-info` | Maintainer needs more information from the reporter |
| `wontfix` | Won't be implemented |

## Where to Start

If you're new and unsure where to dig in, easy starting points:

- **Documentation fixes** — typos, broken links, unclear sections in any `*.md` file.
- **Spotless violations** — run `./gradlew spotlessCheck` and fix anything it flags.
- **Groovy presets** — fill in or extend an arena under [infinity/zone/conf/](infinity/zone/conf/) or [infinity/zone/arenas/](infinity/zone/arenas/).
- **Failing or skipped tests** — `./gradlew test` and look for things to investigate.

If none of that fits and you'd like a maintainer's blessing on a direction first, ping in [Discord](https://discord.gg/FXqNB6N) — happy to point you at something.

## Project Rules

These rules are mirrored from [CLAUDE.md](CLAUDE.md). **CLAUDE.md is canonical** — if this list disagrees with it, CLAUDE.md wins.

| Rule | Why | Canonical |
|---|---|---|
| Use `final` for method parameters | Project convention; catches accidental reassignment | [CLAUDE.md §Always-on Rules #1](CLAUDE.md) |
| SPDX-only `BSD-3-Clause` license header on every source file | Licensing; copy from any existing file | [CLAUDE.md §Always-on Rules #2](CLAUDE.md) |
| Tuning knobs go in Groovy, not Java | Gameplay balance shouldn't require a recompile | [CLAUDE.md §Always-on Rules #4](CLAUDE.md) |
| Layer boundaries: `api/` is data + interfaces only; client observes, server owns | Keeps the api ↔ server ↔ client split honest; enforced by [LayerDependencyTest](infinity/src/test/java/infinity/architecture/LayerDependencyTest.java) | [.claude/rules/](.claude/rules/) |

### Path-scoped rules

When you touch the corresponding directory, the rules below apply. The full text lives in [.claude/rules/](.claude/rules/) — read the matching file before editing.

| If you touch… | Rule |
|---|---|
| `api/src/main/java/infinity/es/**` | [components.md](.claude/rules/components.md) — immutable, no-arg constructor, register cross-wire components |
| `api/src/**` | [api-contracts.md](.claude/rules/api-contracts.md) — data + interfaces only; no deps on server/client |
| `client/**` and `*AppState` files | [client-read-only.md](.claude/rules/client-read-only.md) — client observes, server owns; writes via RMI |
| `infinity/systems/**` | [systems.md](.claude/rules/systems.md) — logic-in-systems; no duplicate component producers |
| `infinity/**` (Java) | [entity-sets.md](.claude/rules/entity-sets.md) — release `EntitySet` in `terminate()` |
| `infinity/**` (Java) | [world-coordinates.md](.claude/rules/world-coordinates.md) — use `TileId` APIs, not `* 1024` |
| `api/src/main/java/infinity/config/**`, `api/src/main/java/infinity/es/ship/**` | [config-pattern.md](.claude/rules/config-pattern.md) — template (`*Config`) vs instance (components); spawn projects template → component |
| `infinity/systems/ship/applier/**` | [prize-applier.md](.claude/rules/prize-applier.md) — match canonical Subspace prize semantics from REFERENCE.md |
| Any TTL/decay code | [decay-ttl.md](.claude/rules/decay-ttl.md) — `Decay` is the only TTL mechanism |

## Code Style

- **Run Spotless before submitting:** `./gradlew spotlessApply`. The build will fail if formatting is wrong.
- **License header on every source file.** SPDX-only — `// SPDX-License-Identifier: BSD-3-Clause` followed by `// Copyright (c) 2018-2026 Asser Fahrenholz`. Full text is in [LICENSE.md](LICENSE.md).
- **`final` on method parameters.** Always.
- **Comments: default to none.** Only write a comment when the *why* is non-obvious — a hidden constraint, a subtle invariant, a workaround for a specific bug. Don't explain what well-named code already says.

## Submitting a Pull Request

1. **Fork the repository** and push your branch to your fork.
2. **Open a PR against `infinity`** — this is the project's default branch, *not* `main`.
3. **One concern per PR.** Don't combine unrelated changes. If a refactor and a feature are tangled, split them.
4. **Open as Draft** if you want early feedback or the PR isn't ready for review.

### Commit messages

Conventional Commits style is recommended but not required. Format: `type(scope): subject`. Examples from recent history:

```
docs(readme): rewrite as best-practice README
test(harness): extend spawn-projection harness to prize + projectile pillars
docs(prd): add github-pages do-over PRD
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`.

### Linking issues

If your PR addresses an issue, link it in the description (`Fixes #123`). Required only for non-trivial PRs — typo and small docs fixes don't need an issue first.

### PR size

Aim for a single concern and a reviewable diff. PRs that are too large to review will be asked to split before merge — this is a maintainer judgment call, not a hard line count.

### Verification

**Automated tests are preferred.** If your change is testable, add a test under the relevant module's `src/test/`. Run `./gradlew build` to make sure existing tests still pass.

**When automated testing isn't feasible** (most gameplay-bearing changes — there's no automated gameplay test harness yet), launch the game and verify your change manually, then include a short *"what I tested"* note in the PR description: what scenario you exercised and what you observed. Pure refactors with no behavior change can self-attest.

### Pre-PR checklist

- [ ] `./gradlew build` passes locally
- [ ] `./gradlew spotlessApply` run, no diff left over
- [ ] SPDX-only `BSD-3-Clause` header on any new source files
- [ ] Followed the path-scoped rules in [.claude/rules/](.claude/rules/) for files you touched
- [ ] One concern per PR
- [ ] Linked an issue if applicable
- [ ] Automated test added, **or** a *"what I tested"* note in the PR description
- [ ] Targeted the `infinity` branch

## Questions

Join us on [Discord](https://discord.gg/FXqNB6N).
