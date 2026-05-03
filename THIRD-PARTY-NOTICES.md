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

## Subspace community maps

**Paths:**

- `infinity/assets/Maps/*.lvl`
- `infinity/assets/Maps/*.lvz`

**Origin:** Maps and overlay packs created by the Subspace / Continuum
player community, distributed across player-run zones and forums over
the lifetime of the game.

**Status:** Each map's authorship rests with its original author.
Many `.lvl` files contain author attribution inside their eLVL `ATTR`
chunks (e.g. the `MAPCREATOR`, `MAPNAME`, `EMAIL` fields). Subspace
Infinity bundles them under the long-standing community norm of free
sharing for play, with no claim of authorship and no relicensing under
the project `LICENSE`. Map authors retain all rights.

If you authored a map included here and want it removed or attributed
differently, see the contact above.

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
`MatDefs/`, `Shaders/`, `Tilesets/`, `Interface/`, `Blocks/`, the
`Maps/04-2026-trench/` arena and similar Subspace Infinity-authored
arenas, and `assets/Textures/MiniMap/` — are Subspace Infinity's own
work and are covered by the project [`LICENSE.md`](LICENSE.md).
