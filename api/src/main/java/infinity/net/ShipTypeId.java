// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

/** Wire-stable byte identifier for the {@link GameSession#avatar(byte)} RMI; bytes match {@link infinity.Ship#getId()} for playable ships, {@link #SPEC} = spectator. Never renumber. */
public enum ShipTypeId {
  /** Leave playing field — spectator. */
  SPEC((byte) 0),
  WARBIRD((byte) 1),
  JAVELIN((byte) 2),
  SPIDER((byte) 3),
  /** Leviathan — abbreviated to {@code LEVI} to match the legacy server byte name. */
  LEVI((byte) 4),
  TERRIER((byte) 5),
  WEASEL((byte) 6),
  LANCASTER((byte) 7),
  SHARK((byte) 8);

  private final byte wireId;

  ShipTypeId(final byte wireId) {
    this.wireId = wireId;
  }

  /** Wire-stable byte identifier used in the {@link GameSession#avatar(byte)} RMI. */
  public byte wireId() {
    return wireId;
  }

  /**
   * Reverse-lookup of a wire byte back to its enum constant.
   *
   * @param wireId the byte read off the wire (or replayed via testing)
   * @return the matching enum constant
   * @throws IllegalArgumentException if no constant matches the byte
   */
  public static ShipTypeId fromWireId(final byte wireId) {
    for (final ShipTypeId t : values()) {
      if (t.wireId == wireId) {
        return t;
      }
    }
    throw new IllegalArgumentException("Unknown ShipTypeId wire id: " + wireId);
  }
}
