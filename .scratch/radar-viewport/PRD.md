# Client/Radar: Implement a new viewport in a RadarState client side

Status: ready-for-human
Cross-ref: [GH #17](https://github.com/assofohdz/subspace-infinity/issues/17)
Labels: enhancement, help wanted, area:client, backlog

**Situation:** Currently the radar is not implemented.

**Ask:** Implement a client side radar. Should be a new viewport that only loads spatials based on a new `radar-shapeinfo-component`. This component defines how entities look on the radar. The `RadarState` should ask for entities that has `BodyPosition` + `SpawnPosition` + `RadarShapeInfo`, and then attach the given spatials to a `radar-root-node`. It should work much like the `ModelViewState` that handles the main scene view, but should attach to a given `radar-root-node` instead.

## Comments
