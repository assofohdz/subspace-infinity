// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

/**
 * Wire-protocol byte identifier for the {@link GameSession#action(byte)} RMI:
 * which consumable action the player triggered (place brick, fire thor,
 * warp, …).
 *
 * <p>Lives in {@code api/} so client RMI dispatch and server RMI receiver
 * share the same canonical byte mapping without the client having to import
 * server internals. Pre-cleanup the same bytes lived as
 * {@code public static final byte} constants on
 * {@code infinity.systems.ship.ConsumableSystem}; clients imported them
 * directly, which only slipped past {@code LayerDependencyTest} because the
 * Java compiler inlines {@code public static final byte} constants at the
 * call site (no bytecode dependency). The enum closes that layering hole.
 *
 * <p>The byte values are wire-stable. Never renumber a constant — wire
 * compatibility depends on the byte value. Add new constants at the end
 * with the next unused byte.
 */
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
