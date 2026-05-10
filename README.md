# Subspace Infinity

A cross-platform [Subspace Continuum](https://store.steampowered.com/app/352700/Subspace_Continuum/) client and server reimagined in Java — built for extensibility, modularity, and modern multiplayer infrastructure.

[![Build](https://github.com/assofohdz/Subspace-Infinity/actions/workflows/gradle.yml/badge.svg)](https://github.com/assofohdz/Subspace-Infinity/actions/workflows/gradle.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=assofohdz_subspace-infinity&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=assofohdz_subspace-infinity)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENSE.md)
[![Latest Release](https://img.shields.io/github/v/release/assofohdz/Subspace-Infinity)](https://github.com/assofohdz/Subspace-Infinity/releases)

## About

[Subspace Continuum](https://store.steampowered.com/app/352700/Subspace_Continuum/) is a 2D massively-multiplayer online game from the late 1990s, where anyone can host their own zone (server) and let clients connect.

Subspace Infinity is a from-scratch Java reimplementation of both the client and the server, built on the [JMonkeyEngine](https://jmonkeyengine.org/) game engine and the [Simsilica](https://github.com/Simsilica) ecosystem.

## Status

**Pre-alpha — single-maintainer project, no playable gameplay yet.**

The engine, networking, ECS, and configuration pipelines are under active development. APIs and gameplay tuning change frequently. Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Goals

- Faithful re-creation of the Subspace Continuum experience on a modern engine.
- Modular, scriptable server architecture so zones, arenas, and game modes can be authored without forking the engine.
- Cross-platform desktop client (Windows, macOS, Linux) with native installers.
- Honest separation between **server-owned game truth** and **client-side rendering**, so a single server can host clients of different vintages.

## Technical Capabilities

Built on the work of [Paul Speed](https://github.com/pspeed42) and the [jMonkeyEngine community](https://hub.jmonkeyengine.org/):

| Concern | Library |
|---|---|
| Game engine & rendering | [JMonkeyEngine 3](https://jmonkeyengine.org/) |
| Entity Component System | [Zay-ES](https://github.com/jMonkeyEngine-Contributions/zay-es) |
| Server game loop & systems | [SiO2](https://github.com/Simsilica/SiO2) |
| Networking & state sync | [SimEthereal](https://github.com/Simsilica/SimEthereal) |
| World paging | [Pager](https://github.com/Simsilica/Pager) |
| GUI | [Lemur](https://github.com/jMonkeyEngine-Contributions/Lemur) |
| Physics, blocks, world grid | [MOSS](https://github.com/assofohdz/moss) |
| Configuration scripting | Groovy DSL with hot reload |

## Download / Play

Public builds are published to [Itch.io](https://assofohdz.itch.io/subspace-infinity).

What changed in each release: [RELEASE-NOTES.md](RELEASE-NOTES.md).

## For Developers

Build instructions, prerequisites, and environment setup live in [BUILDING.md](BUILDING.md).

Other developer documentation:

| Document | Description |
|---|---|
| [docs/setup-guide.md](docs/setup-guide.md) | Full development environment setup |
| [docs/developer-guide.md](docs/developer-guide.md) | In-depth developer guide |
| [docs/quick-reference.md](docs/quick-reference.md) | Common commands and quick info |
| [CONTEXT.md](CONTEXT.md) | Project domain and architectural language |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Guidelines for contributors |

## Community

Join the Subspace Infinity Discord:

**[discord.gg/FXqNB6N](https://discord.gg/FXqNB6N)**

### Related Subspace communities

- [Quantum Space](https://discord.gg/gvtnSAcy)
- [Subspace Continuum](https://discord.gg/y3AanC8Z)
- [Extreme Games](https://discord.gg/hY3gjeJ9)

## AI Disclosure

This project uses generative AI in the following areas:

- **Ambient soundtracks** — background music generated via [Suno](https://suno.com/) AI platform. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for attribution and creator consent.

No other generative AI is used in gameplay code, assets, or documentation.

## Acknowledgments

Thanks to the original Subspace and Continuum developers, POiD, Grelminar, Gigamon, and the many mapmakers and module authors whose work this project builds on.

### Related Projects

- [ASSS](https://bitbucket.org/grelminar/asss) — A Small Subspace Server
- [SubspaceServer](https://github.com/gigamon-dev/SubspaceServer) — Gigamon's server
- [Trench Wars Core](https://github.com/Trench-Wars/twcore)
- [Subspace on Wikipedia](https://en.wikipedia.org/wiki/SubSpace_(video_game))

## License

BSD-3-Clause — see [LICENSE.md](LICENSE.md). The license covers Subspace Infinity's own code and assets only.

Material with separate provenance is documented in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md):

- Original Subspace / Continuum game files (`*.bm2`, `*.wa2`) bundled for compatibility — VIE / Continuum origin, no redistribution license held.
- Community-made maps and overlays (`*.lvl`, `*.lvz`) — original authors retain rights.
- Subspace zone snapshots (`Maps/04-2026-trench/`) — community zone content.
- Subspace zone configurations (`infinity/zone/conf/deva-04-2026/`, `svs*/`, `trench-04-2026/` and the corresponding arenas) — settings ported from real community zones; the Groovy DSL is mine, the canonical values are community work.
- MillionthVector textures — CC BY 4.0, attribution preserved.
- Simsilica-derived source files retain their original BSD-3 headers (clause 1: keep upstream copyright notices).

If you are a rights holder for any of the above and want it removed, ping in [Discord](https://discord.gg/FXqNB6N).
