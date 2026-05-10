# Logging Policy — Console vs File

Where each log message ends up depends on its level and on the per-logger config in [`infinity-server/src/main/resources/log4j2.xml`](../infinity-server/src/main/resources/log4j2.xml). This file is the rulebook so you know which appender to look at when chasing a bug.

## The three appenders

| Appender | File | Purpose | Audience |
|---|---|---|---|
| **Console** | stdout | Live signal while running the dev loop. Watch in your terminal as you play. | You, right now. |
| **File** | [`infinity-server/logs/infinity.log`](../infinity-server/logs/infinity.log) | Persistent, rolling (10 MB × 5). Captures more detail than console for post-mortem. | You, after a bug already happened. |
| **ErrorFile** | [`infinity-server/logs/infinity-errors.log`](../infinity-server/logs/infinity-errors.log) | Crash diagnosis only — ERROR threshold filter. Rolling (5 MB × 3). | You, when the server died unexpectedly. |

## Level-routing intent

| Level | Console | File | ErrorFile |
|---|---|---|---|
| `ERROR` | Yes (loud) | Yes | Yes |
| `WARN` | Yes | Yes | — |
| `INFO` | Yes | Yes | — |
| `DEBUG` | **No** by default — only when a per-logger override is set | Yes (for `infinity.*`) | — |
| `TRACE` | No | No (unless explicit override) | — |

**Rule of thumb:**
- If you're watching the terminal and want to see something, use `INFO` or higher.
- If it's a verbose-but-useful diagnostic for post-mortem (per-tick state, contact streams, projection details), use `DEBUG` — it goes to the file, not console.
- If something is genuinely wrong, `WARN` (recoverable) or `ERROR` (broken).

## Where to look first

| You're investigating... | Look here first |
|---|---|
| "What just happened on screen?" / "Why did this fire?" | **Console** — current INFO+ stream |
| "What was the state ~10 seconds before the bug?" | **File** — full DEBUG trail with timestamps |
| "Server died — what was the last error?" | **ErrorFile** — every ERROR, never overwritten by INFO/DEBUG noise |
| Single specific class flood | Search `File` for the class FQN; promote that logger to `DEBUG` if needed |

## Per-logger overrides (when to add one)

The default level for `infinity.*` packages is set per-package in [`log4j2.xml`](../infinity-server/src/main/resources/log4j2.xml#L26). Add a per-class entry only when:

- **Silencing noise** — a system logs INFO so often it drowns the console (then set the logger to WARN, e.g. `infinity.systems.ContactSystem` and `infinity.server.DefaultColumnDb`).
- **Active investigation** — temporarily promote a logger to `DEBUG` to capture verbose detail in the console (mark the change with a TODO comment so it's demoted later, e.g. the multi-arena `ContactSystem` debug bumps used during Phase 2.3 / 3.2).
- **Third-party noise** — a Simsilica / jME class spams at INFO that we don't care about (set to WARN/FATAL, e.g. the existing `com.simsilica.mblock.*` entries).

Don't add per-logger entries just to mirror the default routing — keep the override list short.

## What goes in code as which level

When choosing a level for a new log statement:

| Use... | When... |
|---|---|
| `log.error(...)` | An invariant is violated, a request can't be completed, or the server should crash-loop on it. Goes to all three appenders. |
| `log.warn(...)` | Something unexpected but recoverable: missing config, fallback path taken, retry succeeded. Visible in console + file. |
| `log.info(...)` | Lifecycle / state-change events that a developer running the game wants to see: arena loaded, ship spawned, prize picked up, member entered arena. **One line per real event** — not per tick. Visible in console + file. |
| `log.debug(...)` | Per-tick state, per-contact details, per-frame stats. Wrapped in `if (log.isDebugEnabled())` only when the message construction itself is expensive (string concats with many tokens). Goes to file only. |
| `log.trace(...)` | Avoid unless you really need it — trace is for "every value in this hot loop" debugging. Off by default everywhere. |

## How to flip a logger temporarily

For one-off investigation without editing the XML:

```bash
# JVM arg to override at startup (works alongside log4j2.xml):
./gradlew :infinity-client:run -Dinfinity.systems.ContactSystem.level=DEBUG
```

Or edit [`log4j2.xml`](../infinity-server/src/main/resources/log4j2.xml), restart, and add a comment marking the override as temporary so a future session knows to demote.

## Common patterns to match

- `infinity.systems.ContactSystem` is at WARN by default — flips to DEBUG only during contact-pipeline work.
- `infinity.settings` is at INFO so `ShipSpawnSystem` projection warnings surface but per-projection logs stay file-only.
- `com.simsilica.mphys` is at DEBUG, `com.simsilica.mphys.BinIndex` and `Bin` are FATAL — broad MOSS detail without per-bin spam.
