#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-3-Clause
# Copyright (c) 2018-2026 Asser Fahrenholz
"""Flow-field generation checker for Subspace .lvl maps (bot-AI v2 / ADR-0011, slice #03).

Replicates the server's nav math end-to-end so a goal->source navigation can be audited offline:

  * passability: LevelFile 10-bit (0x3FF) tile parse + LegacyMapProjector axis-flip
                 (world cell (X,Z) solid iff tile (1023-X, 1023-Z) != 0) + optional clearance erosion
  * DijkstraDistanceField: 8-connected, octile costs, no corner-cutting, built from the GOAL cell
  * FieldGradient: central-difference descent direction (toward the goal) at the SOURCE cell

Reports whether the goal is reachable from the source (finite distance), the gradient the bot would
steer along, and a local ASCII window around the source so you can confirm walls land where expected
(ground-truth check on the flip). World cells; one tile = one world unit.

Usage:
  scripts/lvl_flowfield_check.py <map.lvl> --goal X,Z --src X,Z [--clearance N] [--window N]
"""
import argparse
import heapq
import math
import struct
import sys

GRID = 1024
SQRT2 = math.sqrt(2.0)
DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)]


def parse_solid(path):
    data = open(path, "rb").read()
    off = struct.unpack_from("<I", data, 2)[0] if data[:2] == b"BM" else 0
    solid = set()
    pos = off
    while pos + 4 <= len(data):
        v = struct.unpack_from("<I", data, pos)[0]
        pos += 4
        if (v >> 24) & 0xFF != 0:
            solid.add((v & 0x3FF, (v >> 12) & 0x3FF))  # (x, y), 10-bit like LevelFile
    return solid


def build_passable(solid, clearance):
    grid = bytearray(GRID * GRID)
    for z in range(GRID):
        base = z * GRID
        for x in range(GRID):
            grid[base + x] = 0 if (GRID - 1 - x, GRID - 1 - z) in solid else 1
    if clearance <= 0:
        return grid
    out = bytearray(GRID * GRID)

    def ok(x, z):
        return 0 <= x < GRID and 0 <= z < GRID and grid[z * GRID + x]

    for z in range(GRID):
        for x in range(GRID):
            clear = all(
                ok(x + dx, z + dz)
                for dz in range(-clearance, clearance + 1)
                for dx in range(-clearance, clearance + 1)
            )
            out[z * GRID + x] = 1 if clear else 0
    return out


def passable(grid, x, z):
    return 0 <= x < GRID and 0 <= z < GRID and grid[z * GRID + x]


def dijkstra(grid, gx, gz, clearance=None, hull_footprint=0, penalty=0.0):
    """Distances from goal (gx,gz) outward; matches DijkstraDistanceField (octile, no corner-cut).

    When clearance + hull_footprint + penalty are non-zero, adds a soft-clearance cost penalty
    (bot-ai-v3 B8): step cost = base + penalty * max(0, hull_footprint - clearance[dest]). Wall-
    adjacent cells stay navigable but cost more, so the flow prefers centre-of-corridor without
    forbidding edges. clearance=None means flat unit cost (current server behaviour).
    """
    INF = float("inf")
    dist = [INF] * (GRID * GRID)
    if not passable(grid, gx, gz):
        return dist
    dist[gz * GRID + gx] = 0.0
    pq = [(0.0, gx, gz)]
    while pq:
        d, x, z = heapq.heappop(pq)
        if d > dist[z * GRID + x]:
            continue
        for dx, dz in DIRS:
            nx, nz = x + dx, z + dz
            if not passable(grid, nx, nz):
                continue
            diag = dx != 0 and dz != 0
            if diag and (not passable(grid, x + dx, z) or not passable(grid, x, z + dz)):
                continue  # no corner cutting
            step = SQRT2 if diag else 1.0
            if clearance is not None:
                deficit = hull_footprint - clearance[nz * GRID + nx]
                if deficit > 0:
                    step += penalty * deficit
            nd = d + step
            ni = nz * GRID + nx
            if nd < dist[ni]:
                dist[ni] = nd
                heapq.heappush(pq, (nd, nx, nz))
    return dist


def hull_navigable(grid):
    """Mask cells the hull-2 ship physically can't occupy (bot-ai-v3 B8 follow-up).

    Four 3×3 pinch patterns block a cell:
      * N+S walls (vertical 1-cell-tall corridor)
      * W+E walls (horizontal 1-cell-wide corridor)
      * NW+SE diagonal walls
      * NE+SW diagonal walls (the L-corner case)

    OOB does NOT count as a wall — map edges aren't physical obstacles. Mirrors
    NavGrids.hullNavigable in Java.
    """
    out = bytearray(GRID * GRID)
    def wallIn(x, z):
        return 0 <= x < GRID and 0 <= z < GRID and not grid[z * GRID + x]
    for z in range(GRID):
        for x in range(GRID):
            if not grid[z * GRID + x]:
                continue
            n  = wallIn(x,     z - 1)
            s  = wallIn(x,     z + 1)
            ew = wallIn(x - 1, z)
            ee = wallIn(x + 1, z)
            nw = wallIn(x - 1, z - 1)
            ne = wallIn(x + 1, z - 1)
            sw = wallIn(x - 1, z + 1)
            se = wallIn(x + 1, z + 1)
            pinched = (n and s) or (ew and ee) or (nw and se) or (ne and sw)
            out[z * GRID + x] = 0 if pinched else 1
    return out


def build_clearance(grid, max_clearance):
    """Multi-source BFS from walls + OOB; clearance[z*G+x] = min(max, Chebyshev dist to wall).

    Mirrors NavGrids.clearanceField (Java). Wall cells store 0; deep-open cells cap at max_clearance.
    Used by dijkstra(..., clearance=...) for soft-clearance routing (bot-ai-v3 B8 + B11).
    """
    clear = [0] * (GRID * GRID)
    from collections import deque

    q = deque()
    for z in range(GRID):
        for x in range(GRID):
            if not grid[z * GRID + x]:
                clear[z * GRID + x] = 0
                q.append((x, z))
            else:
                clear[z * GRID + x] = -1
    while q:
        x, z = q.popleft()
        nxt = clear[z * GRID + x] + 1
        if nxt > max_clearance:
            continue
        for dz in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                nx, nz = x + dx, z + dz
                if nx < 0 or nz < 0 or nx >= GRID or nz >= GRID:
                    continue
                if clear[nz * GRID + nx] == -1:
                    clear[nz * GRID + nx] = nxt
                    q.append((nx, nz))
    for i in range(GRID * GRID):
        if clear[i] == -1:
            clear[i] = max_clearance
    return clear


def gradient(dist, x, z):
    """Descent direction at (x,z) — central differences toward lower distance (toward goal)."""
    c = dist[z * GRID + x]
    if not math.isfinite(c):
        return (0.0, 0.0)

    def s(xx, zz):
        if 0 <= xx < GRID and 0 <= zz < GRID and math.isfinite(dist[zz * GRID + xx]):
            return dist[zz * GRID + xx]
        return c

    gx = (s(x + 1, z) - s(x - 1, z)) * 0.5
    gz = (s(x, z + 1) - s(x, z - 1)) * 0.5
    mag = math.hypot(gx, gz)
    if mag < 1e-9:
        return (0.0, 0.0)
    return (-gx / mag, -gz / mag)  # descent (toward goal)


def window(grid, cx, cz, r):
    rows = []
    for z in range(cz - r, cz + r + 1):
        row = []
        for x in range(cx - r, cx + r + 1):
            if x == cx and z == cz:
                row.append("S")
            elif not (0 <= x < GRID and 0 <= z < GRID):
                row.append("?")
            else:
                row.append("." if grid[z * GRID + x] else "#")
        rows.append("".join(row))
    return rows


def md_table(title, xs, zs, cell_fn):
    """Render a labelled markdown table for cells (xs × zs); cell_fn(x,z) returns the cell string."""
    out = [f"### {title}", ""]
    header = "| y\\x | " + " | ".join(str(x) for x in xs) + " |"
    sep = "|" + "|".join(["---"] * (len(xs) + 1)) + "|"
    out += [header, sep]
    for z in zs:
        cells = [cell_fn(x, z) for x in xs]
        out.append(f"| **{z}** | " + " | ".join(cells) + " |")
    out.append("")
    return out


def md_passability(grid, sx, sz, gx, gz, xs, zs):
    def cell(x, z):
        if x == sx and z == sz:
            return "**S**"
        if x == gx and z == gz:
            return "**G**"
        if not (0 <= x < GRID and 0 <= z < GRID):
            return "?"
        return "·" if grid[z * GRID + x] else "█"
    return md_table("raw passability (█=wall, ·=open, **S**=src, **G**=goal)", xs, zs, cell)


def md_clearance(grid, clear, sx, sz, gx, gz, xs, zs):
    def cell(x, z):
        if x == sx and z == sz:
            return "**S**"
        if x == gx and z == gz:
            return "**G**"
        if not (0 <= x < GRID and 0 <= z < GRID):
            return "?"
        if not grid[z * GRID + x]:
            return "█"
        return str(clear[z * GRID + x])
    return md_table("clearance (cells-to-wall; █=wall, **S**=src, **G**=goal)", xs, zs, cell)


def md_flow(grid, dist, sx, sz, gx, gz, xs, zs):
    def cell(x, z):
        if x == sx and z == sz:
            return "**S**"
        if x == gx and z == gz:
            return "**G**"
        if not (0 <= x < GRID and 0 <= z < GRID):
            return "?"
        if not grid[z * GRID + x]:
            return "█"
        dx, dz = gradient(dist, x, z)
        if dx or dz:
            return arrow(dx, dz)
        d = dist[z * GRID + x]
        return "·" if math.isinf(d) else "*"
    return md_table("flow (arrows=descent toward goal; ·=unreachable, *=at-goal)", xs, zs, cell)


ARROWS = ["→", "↘", "↓", "↙", "←", "↖", "↑", "↗"]  # E SE S SW W NW N NE (gz+ = south)


def arrow(gx, gz):
    idx = int(round(math.atan2(gz, gx) / (math.pi / 4))) % 8
    return ARROWS[idx]


def flowviz(grid, dist, gx, gz, sx, sz, pad):
    """Flow-field arrows over the bbox of goal+src (+pad): # wall, G goal, S src, arrow=flow toward
    goal, '*' open-but-no-flow (at goal / unreachable). Shows whether the field routes around walls."""
    x0, x1 = max(0, min(gx, sx) - pad), min(GRID - 1, max(gx, sx) + pad)
    z0, z1 = max(0, min(gz, sz) - pad), min(GRID - 1, max(gz, sz) + pad)
    rows = []
    for z in range(z0, z1 + 1):
        row = []
        for x in range(x0, x1 + 1):
            if x == gx and z == gz:
                row.append("G")
            elif x == sx and z == sz:
                row.append("S")
            elif not grid[z * GRID + x]:
                row.append("#")
            else:
                dx, dz = gradient(dist, x, z)
                row.append(arrow(dx, dz) if (dx or dz) else "*")
        rows.append("".join(row))
    return rows


def main():
    ap = argparse.ArgumentParser(description="Audit flow-field goal->source navigation for a .lvl map.")
    ap.add_argument("lvl")
    ap.add_argument("--goal", required=True, help="goal cell X,Z (field built from here)")
    ap.add_argument("--src", required=True, help="source cell X,Z (bot position)")
    ap.add_argument("--clearance", type=int, default=1,
                    help="hard erosion (2N+1) — pass --soft-clearance to use the new soft-clearance "
                         "cost penalty instead (bot-ai-v3 B8). 0 disables erosion entirely.")
    ap.add_argument("--soft-clearance", type=int, default=0, metavar="N",
                    help="soft-clearance Chebyshev distance cap (cells); enables the cost-penalty "
                         "model — wall-adjacent cells stay navigable but cost more. When set, "
                         "--clearance is ignored.")
    ap.add_argument("--soft-penalty", type=float, default=2.0, metavar="P",
                    help="additional step cost per unit clearance-deficit when --soft-clearance is "
                         "set (step = base + P*max(0, N-clearance[dest])). Default 2.0.")
    ap.add_argument("--window", type=int, default=6, help="ASCII half-window around the source")
    ap.add_argument(
        "--spawn-frame",
        type=int,
        default=0,
        metavar="BOUND",
        help="treat --goal/--src as spawn/.lvl coords and convert to world via (BOUND - c); "
        "BOUND is the arena world max (e.g. 1024). RandomRadiusSpawnPlacement uses world = max - coord, "
        "so editor/observed coords need this to land on the cell the bot actually occupies.",
    )
    ap.add_argument(
        "--goal-block",
        type=int,
        default=0,
        metavar="N",
        help="quantize the goal to the centre of an N-tile block, exactly as SteerApproachTarget "
        "(GOAL_BLOCK) does. 0 = use the goal cell as-is.",
    )
    ap.add_argument(
        "--viz",
        type=int,
        default=0,
        metavar="PAD",
        help="render the flow field as arrows over the goal+src bounding box, padded by PAD cells",
    )
    ap.add_argument(
        "--md",
        type=int,
        default=0,
        metavar="HALF",
        help="emit markdown tables (passability + clearance + flow) for a HALF-window square "
             "around the src cell — for pasting into review notes / .scratch/.",
    )
    args = ap.parse_args()

    gx, gz = (int(v) for v in args.goal.split(","))
    sx, sz = (int(v) for v in args.src.split(","))
    if args.spawn_frame:
        gx, gz = args.spawn_frame - gx, args.spawn_frame - gz
        sx, sz = args.spawn_frame - sx, args.spawn_frame - sz
        print(f"[spawn-frame] goal->world ({gx},{gz})  src->world ({sx},{sz})")
    if args.goal_block:
        b = args.goal_block
        qgx = (gx // b) * b + b // 2
        qgz = (gz // b) * b + b // 2
        print(f"[goal-block {b}] goal ({gx},{gz}) -> block-centre ({qgx},{qgz})")
        gx, gz = qgx, qgz
    use_soft = args.soft_clearance > 0
    erode = 0 if use_soft else args.clearance
    grid = build_passable(parse_solid(args.lvl), erode)
    # Routing grid for soft-clearance: hull-pinch-masked raw passable (B8 regression fix).
    # `grid` stays the raw map for LoS / src+goal passable reports; `route_grid` drives Dijkstra.
    route_grid = hull_navigable(grid) if use_soft else grid
    clear = build_clearance(grid, args.soft_clearance) if use_soft else None

    mode = (f"soft-clearance N={args.soft_clearance} penalty={args.soft_penalty} hull-pinch=on"
            if use_soft else f"hard-erosion clearance={args.clearance}")
    print(f"{args.lvl}  {mode}")
    print(f"goal ({gx},{gz}) passable={passable(grid,gx,gz)}   src ({sx},{sz}) passable={passable(grid,sx,sz)}")
    nbrs = [(dx, dz) for dx, dz in DIRS if not passable(grid, sx + dx, sz + dz)]
    print(f"src wall-neighbours: {nbrs if nbrs else 'none (open on all 8 sides)'}")

    dist = (dijkstra(route_grid, gx, gz, clear, args.soft_clearance, args.soft_penalty)
            if use_soft else dijkstra(grid, gx, gz))
    d = dist[sz * GRID + sx]
    if not math.isfinite(d):
        print(f"distance goal->src: UNREACHABLE  -> gradient (0,0) -> SteerApproachTarget FAILS (Pursue)")
    else:
        gxv, gzv = gradient(dist, sx, sz)
        toward = (gx - sx, gz - sz)
        print(f"distance goal->src: {d:.1f}  gradient=({gxv:+.2f},{gzv:+.2f})  (raw toward-goal=({toward[0]},{toward[1]}))")

    print(f"local map around src (S), window {args.window} (# wall, . open):")
    for line in window(grid, sx, sz, args.window):
        print("  " + line)

    if args.viz:
        print(f"flow field (G goal, S src, arrows=flow toward goal, # wall, * no-flow):")
        for line in flowviz(grid, dist, gx, gz, sx, sz, args.viz):
            print("  " + line)

    if args.md:
        r = args.md
        xs = list(range(max(0, sx - r), min(GRID, sx + r + 1)))
        zs = list(range(max(0, sz - r), min(GRID, sz + r + 1)))
        print()
        for line in md_passability(grid, sx, sz, gx, gz, xs, zs):
            print(line)
        if clear is not None:
            for line in md_clearance(grid, clear, sx, sz, gx, gz, xs, zs):
                print(line)
        for line in md_flow(grid, dist, sx, sz, gx, gz, xs, zs):
            print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
