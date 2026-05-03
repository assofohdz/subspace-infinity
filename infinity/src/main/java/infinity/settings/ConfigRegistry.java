// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.Ship;
import infinity.config.PrizeConfig;
import infinity.config.ShipConfig;
import infinity.config.WeaponsConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Immutable per-arena config snapshot. Holds typed {@code *Config} records
 * produced by the config layer (Groovy script / legacy INI) and consumed by
 * spawn systems and projectile-creation paths.
 *
 * <p>Snapshots are frozen at construction — the internal map is defensively
 * copied and wrapped unmodifiable. Live-reload installs a new snapshot via
 * {@link ConfigRegistrySystem#replace}; readers see either the old or new
 * snapshot, never a torn state.
 *
 * <p>Scope: per-ship templates ({@link #ships}), per-arena weapon-projectile
 * tuning ({@link #weapons}), per-arena prize-spawn defaults ({@link #prize}).
 * Grow by adding per-type slots as new clusters migrate from local Java
 * constants to typed config — see config-pattern.md.
 */
public final class ConfigRegistry {

  /** Reusable empty snapshot. Returned for arenas with no loaded config. */
  public static final ConfigRegistry EMPTY = builder().build();

  private final Map<Ship, ShipConfig> ships;
  private final WeaponsConfig weapons;
  private final PrizeConfig prize;

  private ConfigRegistry(
      final EnumMap<Ship, ShipConfig> shipsSource,
      final WeaponsConfig weapons,
      final PrizeConfig prize) {
    final EnumMap<Ship, ShipConfig> copy = new EnumMap<>(Ship.class);
    copy.putAll(shipsSource);
    this.ships = Collections.unmodifiableMap(copy);
    this.weapons = weapons;
    this.prize = prize;
  }

  /**
   * Look up the config for a ship type. Returns {@code null} if the config
   * layer didn't populate this ship — callers decide whether that's a
   * hard error or a soft "use built-in defaults" fallback.
   */
  @Nullable
  public ShipConfig getShip(final Ship type) {
    return ships.get(type);
  }

  /** Ship types that have an explicit config entry in this snapshot. */
  public Set<Ship> configuredShips() {
    return ships.keySet();
  }

  /**
   * Per-arena weapon-projectile tuning (damage, decay, grav-bomb knobs,
   * burst count). Never {@code null} — the builder defaults to
   * {@link WeaponsConfig#DEFAULTS} when not specified.
   */
  public WeaponsConfig weapons() {
    return weapons;
  }

  /**
   * Per-arena prize-spawn defaults (decay / max count / bounty value).
   * Never {@code null} — the builder defaults to {@link PrizeConfig#DEFAULTS}.
   */
  public PrizeConfig prize() {
    return prize;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Return a copy of this snapshot with {@link #weapons} replaced. Used by
   * Phase B fragment loaders that derive weapon tuning from the per-arena
   * {@code Ini} after the ship snapshot is already installed — keeps both
   * loaders independent without exposing a mutable builder.
   */
  public ConfigRegistry withWeapons(final WeaponsConfig replacement) {
    Objects.requireNonNull(replacement, "weapons");
    final EnumMap<Ship, ShipConfig> source = new EnumMap<>(Ship.class);
    source.putAll(this.ships);
    return new ConfigRegistry(source, replacement, this.prize);
  }

  /** Counterpart to {@link #withWeapons} for the {@code [Prize]} fragment section. */
  public ConfigRegistry withPrize(final PrizeConfig replacement) {
    Objects.requireNonNull(replacement, "prize");
    final EnumMap<Ship, ShipConfig> source = new EnumMap<>(Ship.class);
    source.putAll(this.ships);
    return new ConfigRegistry(source, this.weapons, replacement);
  }

  /**
   * Mutable accumulator for a snapshot. Intended to be short-lived — populate
   * via the config layer, then call {@link #build()} to freeze. Not
   * thread-safe; build from one thread, publish via
   * {@link ConfigRegistrySystem#replace}.
   */
  public static final class Builder {

    private final EnumMap<Ship, ShipConfig> ships = new EnumMap<>(Ship.class);
    private WeaponsConfig weapons = WeaponsConfig.DEFAULTS;
    private PrizeConfig prize = PrizeConfig.DEFAULTS;

    public Builder ship(final Ship type, final ShipConfig config) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(config, "config");
      ships.put(type, config);
      return this;
    }

    public Builder weapons(final WeaponsConfig weapons) {
      this.weapons = Objects.requireNonNull(weapons, "weapons");
      return this;
    }

    public Builder prize(final PrizeConfig prize) {
      this.prize = Objects.requireNonNull(prize, "prize");
      return this;
    }

    public ConfigRegistry build() {
      return new ConfigRegistry(ships, weapons, prize);
    }
  }
}
