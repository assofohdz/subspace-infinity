// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

/**
 * Wire-protocol byte identifier for the {@link GameSession#avatar(byte)} RMI:
 * which ship type the client wants to switch to. {@link #SPEC} means "leave
 * the playing field — go to spectator mode" — the only non-ship enum
 * constant.
 *
 * <p>Lives in {@code api/} so client RMI dispatch and server RMI receiver
 * share the same canonical byte mapping without the client having to import
 * server internals. Pre-cleanup the same bytes lived as
 * {@code public static final byte} constants on
 * {@code infinity.systems.AvatarSystem}; clients imported them directly,
 * which only slipped past {@code LayerDependencyTest} because the Java
 * compiler inlines {@code public static final byte} constants at the call
 * site (no bytecode dependency). The enum closes that layering hole.
 *
 * <p>The byte values are wire-stable. Bytes match Subspace canonical
 * {@code arena.conf} section order ({@code [Warbird], [Javelin], [Spider],
 * [Leviathan], [Terrier], [Weasel], [Lancaster], [Shark]}) and agree with
 * {@link infinity.Ship#getId()} for the playable-ship entries —
 * {@link infinity.Ship} is the ECS-side identity and does not carry
 * {@link #SPEC}. The historical {@code AvatarSystem.LANCASTER}/
 * {@code WEASEL} swap (canon 6=Weasel, 7=Lancaster) was reconciled
 * before this enum was promoted to api/; see the regression test in
 * {@code infinity.ShipTest}.
 *
 * <p>Never renumber a constant — wire compatibility depends on the byte
 * value. Add new constants at the end with the next unused byte.
 */
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
