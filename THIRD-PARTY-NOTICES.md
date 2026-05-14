# Third-Party Notices

The [`LICENSE.md`](LICENSE.md) at the root of this repository (BSD-3-Clause) covers
Subspace Infinity's own source code and assets only. The material listed
below is third-party and is **not** licensed under the project's `LICENSE`.
Each item retains its own provenance and rights status, summarized here.

If you are a rights holder for any of the material listed below and would
like it removed, contact the maintainer in the project [Discord](https://discord.gg/FXqNB6N).

---

## Subspace / Continuum game files

**Paths:**

- `infinity/assets/Textures/Subspace/` (`*.bm2`, `*.png`, `*.gif`, `*.bmp`,
  including `OriginalShips/`)
- `infinity/assets/Sounds/Subspace/` (`*.wa2`)

**Origin:** SubSpace, originally developed and published by Virgin
Interactive Entertainment (VIE) in 1997, and its successor Continuum.

**Status:** No redistribution license is held for these files. They are
included in this repository for compatibility and faithfulness to the
original game's look and feel. Subspace Infinity makes no claim of
authorship over them. The `.bm2` and `.wa2` formats are Subspace-specific
encoded containers and originate from the original game's data files.

This is documented candidly rather than claimed under a permissive
license. If you are the rights holder, see the contact above.

---

## Subspace community maps and zone snapshots

**Paths:**

- `infinity/assets/Maps/*.lvl`
- `infinity/assets/Maps/*.lvz`
- `infinity/assets/Maps/04-2026-trench/` — a snapshot of the Trench
  Survivor / Trench Wars community zone's map and overlay set as of
  April 2026 (`TSL*.lvl`, `pub*.lvl`, `wbduel.lvl`, `javduel.lvl`, plus
  the zone's `.lvz` overlays). Each file in this directory is community
  work; the directory itself is bundled here as a configuration package
  pointing the project at that zone's content.

**Origin:** Maps, overlay packs, and zone snapshots created by the
Subspace / Continuum player community, distributed across player-run
zones and forums over the lifetime of the game.

**Status:** Each map's authorship rests with its original author.
Many `.lvl` files contain author attribution inside their eLVL `ATTR`
chunks (e.g. the `MAPCREATOR`, `MAPNAME`, `EMAIL` fields). Subspace
Infinity bundles them under the long-standing community norm of free
sharing for play, with no claim of authorship and no relicensing under
the project `LICENSE`. Map authors retain all rights.

If you authored a map included here and want it removed or attributed
differently, see the contact above.

---

## Subspace zone configurations

**Paths:**

- `infinity/zone/conf/deva-04-2026/`
- `infinity/zone/conf/svs/`, `svs-league/`, `svs-pb/`, `svs-tce/`, `svs-turf/`
- `infinity/zone/conf/trench-04-2026/`
- `infinity/zone/arenas/deva/`, `infinity/zone/arenas/trench/`

**Origin:** These directories hold settings ported from real Subspace
community zones — Devastation (`deva`), SubSpace Veteran Server
(`svs*`), Trench Wars / Trench Survivor (`trench`) — captured as of
April 2026 where dated. The Groovy DSL form (the `section` /
`shipSection` invocation patterns, the `include` directive, the loader
machinery in `infinity/src/main/java/infinity/settings/`) is Subspace
Infinity's own work. The canonical setting **values** — ship balance
numbers, weapon parameters, prize tables, zone rules — are sourced
from those community zones and reflect choices made by their
maintainers, not by Subspace Infinity.

**Status:** Numerical settings are typically not copyrightable
expression on their own, but the curated *combination* of values that
defines a zone's identity is community work. Subspace Infinity ports
these as faithfully as it can to reproduce play feel and is not the
original author of the configurations. If you operate one of these
zones (or a successor) and want a configuration changed, attributed
differently, or removed, see the contact above.

`infinity/zone/arenas/(default)/` and `infinity/zone/conf/base/` are
Subspace Infinity's own scaffolding (project defaults, not ported
from a specific community zone).

---

## MillionthVector textures (CC BY 4.0)

**Paths:**

- Files in `infinity/assets/Textures/` (root, non-`Subspace/` subdirectory),
  per `infinity/assets/Textures/Credits.txt`.

**Origin:** Released by MillionthVector
(<https://millionthvector.blogspot.de>).

**License:** Creative Commons Attribution 4.0
(<https://creativecommons.org/licenses/by/4.0/>).

**Attribution:** "Textures by MillionthVector
(<https://millionthvector.blogspot.de>), licensed under CC BY 4.0."
Preserve this attribution when redistributing these assets.

---

## AI-generated ambient soundtracks

**Paths:**

- `infinity/assets/Sounds/Ambient/` (pending)

**Origin:** Ambient background music generated using [Suno](https://suno.com/) AI
music generation platform by [@djancarnary](https://suno.com/@djancarnary).

**Status:** The creator has consented to inclusion in this open-source project.
Subspace Infinity makes no claim of independent authorship; the generative AI
soundtracks are credited to and owned by the creator. These files are included
under explicit consent for open-source redistribution.

---

## RandomSelector (Apache 2.0)

**Paths:**

- `api/src/main/java/infinity/util/RandomSelector.java`

**Origin:** Authored by Olivier Grégoire (2015), based on Efraimidis &
Spirakis' weighted random sampling algorithm.

**License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

**Attribution:** "Copyright 2015 Olivier Grégoire. Licensed under the
Apache License, Version 2.0." Preserve this attribution when
redistributing the file or substantial portions of its source.

---

## Bundled `.j3o` files of uncertain origin

**Paths:**

- `infinity/assets/Models/simsilica.j3o`
- `infinity/assets/Models/fighter.j3o`
- `infinity/assets/Light/defaultProbe.j3o`

**Status:** These files were added to the repository in 2020. The
filenames suggest they may originate from Simsilica or jMonkeyEngine 3
sample data (both projects are BSD-3-Clause), but this has not been
positively verified. They are not original Subspace Infinity work and
are not licensed under the project `LICENSE`. If you can confirm or
correct their provenance, see the contact above.

---

## Project's own assets

All other files under `infinity/assets/` — including `Materials/`,
`MatDefs/`, `Shaders/`, `Tilesets/`, `Interface/`, `Blocks/`, and
`assets/Textures/MiniMap/` — are Subspace Infinity's own work and are
covered by the project [`LICENSE.md`](LICENSE.md).
