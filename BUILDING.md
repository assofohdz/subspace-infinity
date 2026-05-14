# Building

## Prerequisites

- **JDK** — see [Toolchain Versions](docs/setup-guide.md#toolchain-versions) for the supported range
- **Dependencies** installed to local Maven repo

## Quick Start

```bash
./gradlew :infinity-client:run
```

## Full Setup

For complete setup instructions including all dependencies:

👉 **[docs/setup-guide.md](docs/setup-guide.md)**

## Common Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build the project |
| `./gradlew :infinity-client:run` | Run the game (Linux/Windows default) |
| `./gradlew :infinity-client:runMac` | Run on macOS (`-XstartOnFirstThread` + headless AWT) |
| `./gradlew :infinity-client:runX11` | Run on Linux/Wayland with X11 backend |
| `./gradlew clean` | Clean build artifacts |
| `./gradlew dependencyUpdates` | Check for dependency updates |

## Test Coverage (JaCoCo)

`./gradlew test` generates coverage reports at
`<module>/build/reports/jacoco/test/` — `html/index.html` for humans,
`jacocoTestReport.xml` for SonarCloud.

`./gradlew check` runs `jacocoTestCoverageVerification` per module and fails
if line OR branch coverage drops below the floor in `gradle.properties`
(`min<Module>LineCoverage` / `min<Module>BranchCoverage`). The pre-push
hook (`.githooks/pre-push`) inherits this gate.

To ratchet up after raising coverage: re-read the report-level LINE/BRANCH
counters from the XML, raise the matching property, commit. Same shape as
the PMD/Checkstyle ceiling ratchet, inverted (minimums not maximums).

## Live-Reload Behaviour (Operator Note)

Editing the Groovy fragment files (`zone/conf/<preset>/*.groovy`,
`zone/arenas/<name>/arena.groovy`) at runtime updates the per-arena
`ConfigRegistry` snapshot. The flow-on effect depends on which fragment
you edited:

- **Ship-stat fragments** (`ships.groovy`) — re-flow into existing live
  ships via `ShipSpawnSystem.reprojectAll()`. Capability stats and
  `*Max` update; Energy/Health/inventory current counts are preserved
  (see `ShipSpawnSystemTuningProjectionTest`).
- **Weapon and prize fragments** (`bombs.groovy`, `bullets.groovy`,
  `prize-weights.groovy`, etc.) — registry update only. Existing
  in-flight projectiles + already-spawned prizes use the OLD config;
  next spawn / next shot picks up the NEW config. Per the template-vs-
  instance split (see [`config-pattern.md`](.claude/rules/config-pattern.md)
  and [ADR-0002](docs/adr/0002-config-component-projection.md)).

When changing an arena fragment, pause new gameplay long enough for the
live shots/prizes to expire if you need the change to apply uniformly.
