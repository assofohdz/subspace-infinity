# Prefer LSP MCP for Java navigation

This repo wires **three** LSP entry points (all in [`.mcp.json`](../../.mcp.json)
or harness-provided):

1. **`mcp__java-lsp__*`** — `stephanj/LSP4J-MCP` wrapping `jdtls`.
   Java-specific symbol navigation. Lowest friction for Java work.
2. **`mcp__language-server__*`** — `isaacphi/mcp-language-server`
   (Go binary, also pointed at `jdtls`). Adds `rename_symbol`,
   `diagnostics`, and line-number-based `edit_file` that the others
   don't expose.
3. **Generic `LSP` tool** — harness-provided; ops selected by the
   `operation` param. Use for `hover`, `goToImplementation`, and
   call-hierarchy ops.

When navigating Java code, **prefer all three over `grep` /
`Bash`-based search** — they understand types, imports, overrides,
and call sites, which text search cannot.

## What's available

**`mcp__java-lsp__*` — Java-specific, no `operation` param.**
Faster to invoke; first stop for Java work.

- `find_symbols(query)` — workspace symbol search by name / pattern.
  Replaces `grep -rn "class FooSystem"`.
- `document_symbols(file)` — class / method / field outline of a
  single file. Replaces `Read` + scrolling.
- `find_definition(file, line, character)` — go-to-def. Replaces
  guessing the package and `grep`-ing.
- `find_references(file, line, character)` — every reference site.
  Replaces `grep -rn "methodName"` (which misses overrides,
  matches comments, and false-positives on string literals).
- `find_interfaces_with_method(method_name)` — interfaces
  containing a method by name. No grep equivalent.

**`mcp__language-server__*` — rename + diagnostics + line-edit.**
Adds ops the others don't expose. All positional ops are
file-based, not symbol-position based.

- `rename_symbol(file, line, character, new_name)` — project-wide
  rename. The closest thing to an actual refactor I have; replaces
  multi-site `Edit` calls when changing a class / method / field
  name. Verify the position lands on the identifier, not whitespace.
- `diagnostics(file)` — compiler warnings + errors for a file.
  Useful after a batch of edits to catch type mismatches and missing
  imports without running the full Gradle build.
- `edit_file(file, edits)` — line-number-based edits. Overlaps with
  the harness `Edit`; prefer `Edit` (exact-string-match is safer
  than line-numbers, which shift on prior edits in the same batch).
- `definition` / `references` / `hover` — overlap with the Java
  MCP and the generic `LSP` tool; pick whichever is already in
  context.

**`LSP` — generic, `operation` selects the action.**
Use when neither MCP exposes the op, or for non-Java files
(Groovy LSP if configured later).

- `goToDefinition` / `findReferences` — overlap with the Java MCP.
- `hover` — docs + type info at a position. Only here.
- `documentSymbol` / `workspaceSymbol` — overlap with the Java MCP.
- `goToImplementation` — implementations of an interface or
  abstract method. Only here. Useful for `BaseInfinitySystem`
  subclasses, RMI interface implementors, `ArenaModule` impls.
- `prepareCallHierarchy` + `incomingCalls` / `outgoingCalls` —
  who-calls-this / what-this-calls. Only here. Replaces
  multi-step grep when tracing a hot-path call chain.

All positional ops use **1-based line + character** (editor
coords). Position must land on the symbol's identifier, not
whitespace or a keyword — `Read` the surrounding line first to
get the exact column.

## When to use which

| Question | Tool |
|---|---|
| "Where is class `Foo` defined?" | `mcp__java-lsp__find_symbols` |
| "What's in this file?" | `mcp__java-lsp__document_symbols` |
| "Who calls `Foo.bar()`?" | `mcp__java-lsp__find_references` (or `incomingCalls` for a hierarchy) |
| "Who implements `ArenaModule`?" | `LSP` with `goToImplementation` |
| "What does `BaseAppState.update` actually do?" | `LSP` with `hover` |
| "Which interfaces declare `apply()`?" | `mcp__java-lsp__find_interfaces_with_method` |
| "Trace `tick()` down N levels" | `LSP` with `prepareCallHierarchy` → `outgoingCalls` |
| "Rename `Foo.bar` to `Foo.baz` everywhere" | `mcp__language-server__rename_symbol` |
| "Did my last edit compile?" | `mcp__language-server__diagnostics` |

## When NOT to use

- **Reading file contents in bulk** — `Read` remains the right tool.
  LSP `documentSymbol` gives you the outline, not the bodies.
- **Searching across non-code files** — Groovy fragments, `.scratch/`
  markdown, build files — `grep` is still right.
- **String / comment / log-message search** — LSP indexes symbols,
  not literals. "Find every place that logs 'spawning'" is a
  `grep` job.
- **First-time exploration when you don't know the symbol name** —
  start with the existing `Explore` / `general-purpose` agents or
  a broad grep, then pivot to LSP once you have a named target.

## Why

Grep over a ~100k-LOC Java codebase produces noisy results: it
matches comments, string literals, javadoc references, partial
identifiers, and same-named symbols across unrelated packages. The
LSP is type-aware — `find_references` on `ShipSystem.spawn()`
returns exactly the call sites for *that* method on *that* class,
not every method named `spawn` in the repo. Over a session that's
the difference between one targeted answer and ten minutes of
filtering grep noise.

The Java MCP is the lower-friction path (no `operation` param, no
positional args for symbol search). Reach for the generic `LSP`
tool only when you need an op the Java MCP doesn't expose
(`hover`, `goToImplementation`, call hierarchy).

## Reference

- [`.mcp.json`](../../.mcp.json) — both MCP server configurations:
  `java-lsp` (LSP4J-MCP) and `language-server`
  (isaacphi/mcp-language-server). Both point at the same `jdtls`
  binary; running both means two jdtls processes / two JDT indices.
- Generic `LSP` tool — provided by the harness, not the project.
- All are deferred tools — load via `ToolSearch` if not in the
  current context before calling.
