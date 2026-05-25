#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-3-Clause
# Copyright (c) 2018-2026 Asser Fahrenholz
"""Bot-nav passability / clearance checker for Subspace .lvl maps.

Mirrors the server's flow-field passability derivation (ADR-0011, slice #03) so you can audit a map
WITHOUT launching the game:

  * LegacyMapProjector's axis-flip:  world cell (X,Z) is solid  iff  tile (1023-X, 1023-Z) is non-zero
  * MapSystemLogic.erodeClearance:   a cell stays passable only if every cell within Chebyshev
                                     distance `clearance` (out-of-bounds = wall) is passable.
                                     Ships are radius-1 (diameter 2 world units) -> default clearance 1.

Reports passable %, 4-connected component sizes (does erosion fragment the navigable space?), a few
named sample cells, and open-corridor widths through a column — the metrics that tell you whether a
bot at A can actually reach a goal at B before vs after clearance.

Usage:
  scripts/lvl_nav_clearance_check.py <map.lvl> [clearance] [--samples x,z[:label] ...] [--col X]
"""
import argparse
import struct
import sys

GRID = 1024


def parse_solid(path):
    """Return the set of (x, y) tile coords with a non-zero tile id.

    .lvl layout: optional BMP tileset (file starts with 'BM'); tile data is a run of little-endian
    uint32, bits 0-11 = x, 12-23 = y, 24-31 = tile id. Only non-empty tiles are stored. The tile
    data offset is bmp bfSize (bytes 2-5) plus any eLVL block; we scan from there and keep entries
    whose x,y land in-grid (garbage from a mis-judged offset falls outside and is dropped).
    """
    data = open(path, "rb").read()
    off = 0
    if data[:2] == b"BM":
        off = struct.unpack_from("<I", data, 2)[0]  # bfSize: BMP (+ any eLVL block) precedes tiles
    solid = set()
    pos = off
    # Match LevelFile.readIn byte-for-byte: tile = i>>24 & 0xff, y = (i>>12) & 0x3FF, x = i & 0x3FF.
    # The 10-bit (0x3FF) masks are load-bearing — 12-bit masks misplace any entry with bits 10-11
    # set, silently relocating walls and changing connectivity (the bug in the first version here).
    while pos + 4 <= len(data):
        v = struct.unpack_from("<I", data, pos)[0]
        pos += 4
        x, y, tid = v & 0x3FF, (v >> 12) & 0x3FF, (v >> 24) & 0xFF
        if tid != 0:
            solid.add((x, y))
    return solid


def build_passable(solid):
    """world cell (x,z) passable iff tile (1023-x, 1023-z) is empty — the projector's flip."""
    grid = bytearray(GRID * GRID)
    for z in range(GRID):
        base = z * GRID
        for x in range(GRID):
            grid[base + x] = 0 if (GRID - 1 - x, GRID - 1 - z) in solid else 1
    return grid


def erode(grid, clearance):
    if clearance <= 0:
        return grid

    def ok(x, z):
        return 0 <= x < GRID and 0 <= z < GRID and grid[z * GRID + x]

    out = bytearray(GRID * GRID)
    for z in range(GRID):
        for x in range(GRID):
            clear = True
            for dz in range(-clearance, clearance + 1):
                for dx in range(-clearance, clearance + 1):
                    if not ok(x + dx, z + dz):
                        clear = False
                        break
                if not clear:
                    break
            out[z * GRID + x] = 1 if clear else 0
    return out


def components(grid):
    seen = bytearray(GRID * GRID)
    sizes = []
    for start in range(GRID * GRID):
        if grid[start] and not seen[start]:
            size = 0
            stack = [start]
            seen[start] = 1
            while stack:
                c = stack.pop()
                size += 1
                cx, cz = c % GRID, c // GRID
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, nz = cx + dx, cz + dz
                    if 0 <= nx < GRID and 0 <= nz < GRID:
                        ni = nz * GRID + nx
                        if grid[ni] and not seen[ni]:
                            seen[ni] = 1
                            stack.append(ni)
            sizes.append(size)
    sizes.sort(reverse=True)
    return sizes


def sample(grid, x, z):
    if not (0 <= x < GRID and 0 <= z < GRID):
        return "OOB"
    return "pass" if grid[z * GRID + x] else "WALL"


def reachable(grid, ax, az, bx, bz):
    """4-connected BFS: can a bot at (ax,az) reach (bx,bz) over passable cells? (what the flow field needs)."""
    if not (0 <= ax < GRID and 0 <= az < GRID and grid[az * GRID + ax]):
        return "src-WALL"
    if not (0 <= bx < GRID and 0 <= bz < GRID and grid[bz * GRID + bx]):
        return "dst-WALL"
    target = bz * GRID + bx
    seen = bytearray(GRID * GRID)
    start = az * GRID + ax
    seen[start] = 1
    stack = [start]
    while stack:
        c = stack.pop()
        if c == target:
            return "REACHABLE"
        cx, cz = c % GRID, c // GRID
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = cx + dx, cz + dz
            if 0 <= nx < GRID and 0 <= nz < GRID:
                ni = nz * GRID + nx
                if grid[ni] and not seen[ni]:
                    seen[ni] = 1
                    stack.append(ni)
    return "UNREACHABLE"


def open_width(grid, z, xc):
    if not (0 <= z < GRID) or not grid[z * GRID + xc]:
        return 0
    w = 1
    x = xc - 1
    while x >= 0 and grid[z * GRID + x]:
        w += 1
        x -= 1
    x = xc + 1
    while x < GRID and grid[z * GRID + x]:
        w += 1
        x += 1
    return w


def main():
    ap = argparse.ArgumentParser(description="Audit bot-nav passability + clearance for a .lvl map.")
    ap.add_argument("lvl")
    ap.add_argument("clearance", nargs="?", type=int, default=1, help="erosion radius in cells (default 1)")
    ap.add_argument("--samples", nargs="*", default=["512,512:center"], help="x,z[:label] cells to report")
    ap.add_argument("--reach", nargs="*", default=[], help="x1,z1:x2,z2 reachability checks (eroded grid)")
    ap.add_argument("--col", type=int, default=512, help="column X for open-width slices")
    args = ap.parse_args()

    solid = parse_solid(args.lvl)
    if not solid:
        print("no solid tiles parsed — wrong offset or empty map?", file=sys.stderr)
        return 1
    xs = [p[0] for p in solid]
    ys = [p[1] for p in solid]
    print(f"{args.lvl}: {len(solid)} solid tiles, lvl bbox x[{min(xs)},{max(xs)}] y[{min(ys)},{max(ys)}]")

    raw = build_passable(solid)
    ero = erode(raw, args.clearance)
    rc, ec = sum(raw), sum(ero)
    total = GRID * GRID
    print(f"passable: raw={rc} ({100*rc/total:.1f}%)  eroded@{args.clearance}={ec} ({100*ec/total:.1f}%)")
    print(f"components: raw={components(raw)[:5]}  eroded={components(ero)[:5]}")
    print("  (one dominant component = navigable space stays connected; many = clearance fragmented it)")

    for spec in args.samples:
        cell, _, label = spec.partition(":")
        x, z = (int(v) for v in cell.split(","))
        print(f"  ({x},{z}) {label or '':12} raw={sample(raw,x,z)} eroded={sample(ero,x,z)}")

    for spec in args.reach:
        a, _, b = spec.partition(":")
        ax, az = (int(v) for v in a.split(","))
        bx, bz = (int(v) for v in b.split(","))
        print(f"  reach ({ax},{az})->({bx},{bz}): {reachable(ero, ax, az, bx, bz)} (eroded grid)")

    print(f"open-width through x={args.col}:")
    for z in (115, 256, 360, 400, 448, 560):
        print(f"  z={z:4} raw={open_width(raw,z,args.col):4} eroded={open_width(ero,z,args.col):4}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
