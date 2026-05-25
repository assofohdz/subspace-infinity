// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.Ship;
import infinity.config.BombConfig;
import infinity.config.BotBrainConfig;
import infinity.config.BotsConfig;
import infinity.config.BrickConfig;
import infinity.config.BulletConfig;
import infinity.config.BurstFireConfig;
import infinity.config.DecoyConfig;
import infinity.config.GravBombConfig;
import infinity.config.MineConfig;
import infinity.config.PortalConfig;
import infinity.config.PrizeConfig;
import infinity.config.PrizeWeightsConfig;
import infinity.config.RepelConfig;
import infinity.config.RocketConfig;
import infinity.config.ShipConfig;
import infinity.config.ThorConfig;
import infinity.modules.ArenaModuleDeclarations;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Immutable per-arena typed {@code *Config} snapshot — defensively-copied at
 * construction, atomic-swap via {@link ConfigRegistrySystem#replace}.
 * Readers see either the old or new snapshot, never a torn state. Slots are
 * keyed internally by their {@code *Config} {@link Class}; adding a new
 * sub-record is one line in {@link #SLOTS} (config class + DEFAULTS sentinel)
 * plus an optional named accessor.
 */
public final class ConfigRegistry {

  private static final String SLOT_TYPE = "slotType";

  // Declared BEFORE EMPTY: EMPTY = builder().build() iterates SLOTS at <clinit>.
  // GravBombConfig has no fragment adapter yet — falls back to DEFAULTS (revisit
  // when canon adds GravBomb-specific knobs). ThorConfig has ThorAdapter / thor.groovy.
  public static final List<Slot<?>> SLOTS =
      List.of(
          Slot.of(BulletConfig.class, BulletConfig.DEFAULTS),
          Slot.of(BombConfig.class, BombConfig.DEFAULTS),
          Slot.of(GravBombConfig.class, GravBombConfig.DEFAULTS),
          Slot.of(MineConfig.class, MineConfig.DEFAULTS),
          Slot.of(BurstFireConfig.class, BurstFireConfig.DEFAULTS),
          Slot.of(RepelConfig.class, RepelConfig.DEFAULTS),
          Slot.of(RocketConfig.class, RocketConfig.DEFAULTS),
          Slot.of(BrickConfig.class, BrickConfig.DEFAULTS),
          Slot.of(DecoyConfig.class, DecoyConfig.DEFAULTS),
          Slot.of(PortalConfig.class, PortalConfig.DEFAULTS),
          Slot.of(ThorConfig.class, ThorConfig.DEFAULTS),
          Slot.of(PrizeConfig.class, PrizeConfig.DEFAULTS),
          Slot.of(PrizeWeightsConfig.class, PrizeWeightsConfig.DEFAULTS),
          Slot.of(BotBrainConfig.class, BotBrainConfig.DEFAULTS),
          Slot.of(BotsConfig.class, BotsConfig.DEFAULTS),
          Slot.of(ArenaModuleDeclarations.class, ArenaModuleDeclarations.DEFAULTS));

  public static final ConfigRegistry EMPTY = builder().build();

  private final Map<Ship, ShipConfig> ships;
  private final Map<Class<?>, Object> slotsByType;

  private ConfigRegistry(
      final Map<Ship, ShipConfig> shipsSource, final Map<Class<?>, Object> slotsSource) {
    final Map<Ship, ShipConfig> shipCopy = new EnumMap<>(Ship.class);
    shipCopy.putAll(shipsSource);
    this.ships = Collections.unmodifiableMap(shipCopy);

    // LinkedHashMap preserves SLOTS iteration order.
    final Map<Class<?>, Object> slotCopy = new LinkedHashMap<>(slotsSource);
    this.slotsByType = Collections.unmodifiableMap(slotCopy);
  }

  /** {@code null} if not configured; callers decide whether to fall back to built-ins. */
  @Nullable
  public ShipConfig getShip(final Ship type) {
    return ships.get(type);
  }

  public Set<Ship> configuredShips() {
    return ships.keySet();
  }

  /** Returns the slot's DEFAULTS sentinel when unset; throws on unregistered slot types. */
  public <T> T get(final Class<T> slotType) {
    Objects.requireNonNull(slotType, SLOT_TYPE);
    final Object value = slotsByType.get(slotType);
    if (value == null) {
      throw new IllegalArgumentException(
          "Unknown config slot " + slotType.getName() + "; register it in ConfigRegistry.SLOTS");
    }
    return slotType.cast(value);
  }

  public BulletConfig bullet() {
    return get(BulletConfig.class);
  }

  public BombConfig bomb() {
    return get(BombConfig.class);
  }

  public GravBombConfig gravBomb() {
    return get(GravBombConfig.class);
  }

  public MineConfig mine() {
    return get(MineConfig.class);
  }

  public BurstFireConfig burst() {
    return get(BurstFireConfig.class);
  }

  public RepelConfig repel() {
    return get(RepelConfig.class);
  }

  public RocketConfig rocket() {
    return get(RocketConfig.class);
  }

  public BrickConfig brick() {
    return get(BrickConfig.class);
  }

  public DecoyConfig decoy() {
    return get(DecoyConfig.class);
  }

  public PortalConfig portal() {
    return get(PortalConfig.class);
  }

  public ThorConfig thor() {
    return get(ThorConfig.class);
  }

  public PrizeConfig prize() {
    return get(PrizeConfig.class);
  }

  public PrizeWeightsConfig prizeWeights() {
    return get(PrizeWeightsConfig.class);
  }

  public BotBrainConfig botBrain() {
    return get(BotBrainConfig.class);
  }

  public BotsConfig bots() {
    return get(BotsConfig.class);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Returns a copy with {@code slotType} replaced. */
  public <T> ConfigRegistry with(final Class<T> slotType, final T replacement) {
    Objects.requireNonNull(slotType, SLOT_TYPE);
    Objects.requireNonNull(replacement, "replacement");
    if (!slotType.isInstance(replacement)) {
      throw new ClassCastException(
          "replacement of type "
              + replacement.getClass().getName()
              + " is not a "
              + slotType.getName());
    }
    if (!slotsByType.containsKey(slotType)) {
      throw new IllegalArgumentException(
          "Unknown config slot " + slotType.getName() + "; register it in ConfigRegistry.SLOTS");
    }
    final Map<Class<?>, Object> next = new LinkedHashMap<>(slotsByType);
    next.put(slotType, replacement);
    return new ConfigRegistry(this.ships, next);
  }

  /** Pairs a {@code *Config} {@link Class} with its DEFAULTS sentinel for {@link Builder} default-population. */
  public static final class Slot<T> {
    private final Class<T> configType;
    private final T defaults;

    private Slot(final Class<T> configType, final T defaults) {
      this.configType = Objects.requireNonNull(configType, "configType");
      this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    public static <T> Slot<T> of(final Class<T> configType, final T defaults) {
      return new Slot<>(configType, defaults);
    }

    public Class<T> configType() {
      return configType;
    }

    public T defaults() {
      return defaults;
    }
  }

  /** Short-lived accumulator; build then publish via {@link ConfigRegistrySystem#replace}. Not thread-safe. */
  public static final class Builder {

    private final Map<Ship, ShipConfig> ships = new EnumMap<>(Ship.class);
    private final Map<Class<?>, Object> slots = new HashMap<>();

    Builder() {
      for (final Slot<?> slot : SLOTS) {
        slots.put(slot.configType(), slot.defaults());
      }
    }

    public Builder ship(final Ship type, final ShipConfig config) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(config, "config");
      ships.put(type, config);
      return this;
    }

    public <T> Builder with(final Class<T> slotType, final T value) {
      Objects.requireNonNull(slotType, SLOT_TYPE);
      Objects.requireNonNull(value, "value");
      if (!slotType.isInstance(value)) {
        throw new ClassCastException(
            "value of type " + value.getClass().getName() + " is not a " + slotType.getName());
      }
      if (!slots.containsKey(slotType)) {
        throw new IllegalArgumentException(
            "Unknown config slot "
                + slotType.getName()
                + "; register it in ConfigRegistry.SLOTS");
      }
      slots.put(slotType, value);
      return this;
    }

    public ConfigRegistry build() {
      return new ConfigRegistry(ships, slots);
    }
  }
}
