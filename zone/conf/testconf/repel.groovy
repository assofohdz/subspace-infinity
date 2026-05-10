// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// testconf [Repel] tuning. testarena uses deva's snappier values —
// higher speed + longer time + bigger distance — so repels feel
// distinctly active during smoke tests.
//
// `distance` is in tiles / world units (Infinity-native; the simulation
// layer doesn't speak in pixels). Subspace canon authors RepelDistance in
// pixels at 16 px/tile — operators porting an SVS server.cfg divide by 16
// (e.g. canon 512 px → 32 tiles).

repel {
    speed     4700
    time      280
    distance  20.625    // = legacy 330 px / 16
}
