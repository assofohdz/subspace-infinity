// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

// `distance` is in tiles / world units (Infinity-native; the simulation
// layer doesn't speak in pixels). Subspace canon authors RepelDistance in
// pixels at 16 px/tile — operators porting an SVS server.cfg divide by 16
// (e.g. canon 512 px → 32 tiles).
repel {
    speed     4000
    time      250
    distance  32.5    // = legacy 520 px / 16
}
