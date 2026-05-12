// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.events;

/** Action selector for {@link infinity.net.GameSession#map} — place or remove a block. */
public enum MapAction {
  /** Place a block at the supplied world coordinate. */
  CREATE,
  /** Remove a block at the supplied world coordinate. */
  DELETE
}
