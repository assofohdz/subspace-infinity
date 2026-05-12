// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

/** Wire-stable byte identifier for the {@link GameSession#action(byte)} RMI. Never renumber. */
public enum ConsumableTypeId {
  PLACEBRICK((byte) 0),
  FIREBURST((byte) 1),
  PLACEDECOY((byte) 2),
  PLACEPORTAL((byte) 3),
  REPEL((byte) 4),
  FIREROCKET((byte) 5),
  FIRETHOR((byte) 6),
  WARP((byte) 7);

  private final byte wireId;

  ConsumableTypeId(final byte wireId) {
    this.wireId = wireId;
  }

  /** Wire-stable byte identifier used in the {@link GameSession#action(byte)} RMI. */
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
  public static ConsumableTypeId fromWireId(final byte wireId) {
    for (final ConsumableTypeId t : values()) {
      if (t.wireId == wireId) {
        return t;
      }
    }
    throw new IllegalArgumentException("Unknown ConsumableTypeId wire id: " + wireId);
  }
}
