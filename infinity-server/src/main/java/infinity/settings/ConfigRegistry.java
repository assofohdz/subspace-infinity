// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.Ship;
import infinity.config.BombConfig;
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
import infinity.config.SpawnConfig;
import infinity.config.ThorConfig;
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
 * Immutable per-arena config snapshot. Holds typed {@code *Config} records
 * produced by the config layer (Groovy script / legacy INI) and consumed by
 * spawn systems and projectile-creation paths.
 *
 * <p>Snapshots are frozen at construction — the internal slot map is
 * defensively copied and wrapped unmodifiable. Live-reload installs a new
 * snapshot via {@link ConfigRegistrySystem#replace}; readers see either the
 * old or the new snapshot, never a torn state.
 *
 * <p>Each weapon-projectile sub-record sits as a direct slot here (post-B1a
 * flatten) — no intermediate {@code WeaponsConfig} grouping struct. Per
 * {@code .scratch/settings-pipeline.md}'s target architecture: the public
 * API is the visual inventory of "what's tunable per arena."
 *
 * <h2>Slot store</h2>
 *
 * Slots are keyed internally by their {@code *Config} {@link Class}. Adding a
 * new typed sub-record is one line in {@link #SLOTS} (config class +
 * {@code DEFAULTS} sentinel) plus an optional named accessor — the wither and
 * Builder setter come for free via the generic
 * {@link #with(Class, Object)} / {@link Builder#with(Class, Object)} pair.
 */
public final class ConfigRegistry {

  /**
   * Single source of truth for which {@code *Config} sub-records this
   * snapshot exposes. One entry per slot; {@link Slot#defaults} is the
   * fallback installed when neither the config layer nor a wither has set
   * the slot. Iteration order matches Builder default-population order so
   * tests / debug printouts stay stable.
   *
   * <p><b>Init-order note:</b> declared <b>before</b> {@link #EMPTY} because
   * static initializers run in source order — {@code EMPTY = builder().build()}
   * iterates this list to populate Builder defaults, so it has to be
   * fully constructed first. Reordering the two declarations triggers a
   * {@code NullPointerException} at {@code <clinit>}.
   */
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
          Slot.of(SpawnConfig.class, SpawnConfig.DEFAULTS));

  /** Reusable empty snapshot. Returned for arenas with no loaded config. */
  public static final ConfigRegistry EMPTY = builder().build();

  private final Map<Ship, ShipConfig> ships;
  private final Map<Class<?>, Object> slots;

  private ConfigRegistry(
      final Map<Ship, ShipConfig> shipsSource, final Map<Class<?>, Object> slotsSource) {
    final Map<Ship, ShipConfig> shipCopy = new EnumMap<>(Ship.class);
    shipCopy.putAll(shipsSource);
    this.ships = Collections.unmodifiableMap(shipCopy);

    // LinkedHashMap preserves SLOTS iteration order; keys are config classes
    // so callers can `get(BulletConfig.class)` typed.
    final Map<Class<?>, Object> slotCopy = new LinkedHashMap<>(slotsSource);
    this.slots = Collections.unmodifiableMap(slotCopy);
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
   * Type-safe slot lookup. Returns the {@code *Config} record stored under
   * {@code slotType}, or that slot's documented {@code DEFAULTS} sentinel if
   * the config layer hasn't installed a value. Never {@code null}.
   *
   * <p>Throws {@link IllegalArgumentException} if {@code slotType} is not a
   * registered slot — catches typos at the seam where a brand-new config
   * record would otherwise silently return {@code null}.
   */
  public <T> T get(final Class<T> slotType) {
    Objects.requireNonNull(slotType, "slotType");
    final Object value = slots.get(slotType);
    if (value == null) {
      throw new IllegalArgumentException(
          "Unknown config slot " + slotType.getName() + "; register it in ConfigRegistry.SLOTS");
    }
    return slotType.cast(value);
  }

  /** Per-arena bullet-bullet tuning. Never {@code null} (defaults to {@link BulletConfig#DEFAULTS}). */
  public BulletConfig bullet() { return get(BulletConfig.class); }

  /** Per-arena bomb tuning. Never {@code null} (defaults to {@link BombConfig#DEFAULTS}). */
  public BombConfig bomb() { return get(BombConfig.class); }

  /** Per-arena gravity-bomb / wormhole tuning. Never {@code null} (defaults to {@link GravBombConfig#DEFAULTS}). */
  public GravBombConfig gravBomb() { return get(GravBombConfig.class); }

  /** Per-arena mine tuning. Never {@code null} (defaults to {@link MineConfig#DEFAULTS}). */
  public MineConfig mine() { return get(MineConfig.class); }

  /** Per-arena burst-firing tuning. Never {@code null} (defaults to {@link BurstFireConfig#DEFAULTS}). */
  public BurstFireConfig burst() { return get(BurstFireConfig.class); }

  /** Per-arena Repel-effect tuning. Never {@code null} (defaults to {@link RepelConfig#DEFAULTS}). */
  public RepelConfig repel() { return get(RepelConfig.class); }

  /** Per-arena Rocket-buff tuning ({@code RocketThrust}/{@code RocketSpeed}). Never {@code null} (defaults to {@link RocketConfig#DEFAULTS}). */
  public RocketConfig rocket() { return get(RocketConfig.class); }

  /** Per-arena Brick tuning ({@code BrickSpan}/{@code BrickTime}). Never {@code null} (defaults to {@link BrickConfig#DEFAULTS}). */
  public BrickConfig brick() { return get(BrickConfig.class); }

  /** Per-arena Decoy tuning ({@code DecoyAliveTime}). Never {@code null} (defaults to {@link DecoyConfig#DEFAULTS}). */
  public DecoyConfig decoy() { return get(DecoyConfig.class); }

  /** Per-arena Portal tuning ({@code WarpPointDelay} portal active time). Never {@code null} (defaults to {@link PortalConfig#DEFAULTS}). */
  public PortalConfig portal() { return get(PortalConfig.class); }

  /** Per-arena Thor projectile tuning ({@code damage}/{@code decayMs}) — consumed by {@code ConsumableLogic}/{@code ConsumableSystem}.
   * Operator authoring path (Groovy adapter + fragment) still TBD; every arena resolves to {@link ThorConfig#DEFAULTS}. */
  public ThorConfig thor() { return get(ThorConfig.class); }

  /**
   * Per-arena prize-spawn defaults (decay / max count / bounty value).
   * Never {@code null} — defaults to {@link PrizeConfig#DEFAULTS}.
   */
  public PrizeConfig prize() { return get(PrizeConfig.class); }

  /**
   * Per-arena prize-spawn weight table — name→weight map, one entry per
   * Subspace prize type. Never {@code null}; defaults to
   * {@link PrizeWeightsConfig#DEFAULTS} (empty map = no prizes spawn).
   * Populated by {@link PrizeWeightsAdapter} from the typed
   * {@code prize-weights.groovy} fragment.
   */
  public PrizeWeightsConfig prizeWeights() { return get(PrizeWeightsConfig.class); }

  /**
   * Per-arena spawn-point data — list of per-team spawns + legacy
   * single-spawn disc radius ({@link SpawnConfig#spawnRadius()}).
   * Never {@code null}; defaults to {@link SpawnConfig#DEFAULTS}
   * (empty teams list = consumer falls back to legacy
   * {@code ArenaConfig.spawnX/spawnZ}). Populated by
   * {@link SpawnAdapter} from the typed {@code spawn.groovy} fragment.
   */
  public SpawnConfig spawn() { return get(SpawnConfig.class); }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Generic wither — return a copy of this snapshot with {@code slotType}
   * replaced by {@code replacement}. Replaces the per-slot wither methods
   * (one for each of the 14 sub-records); adding a new {@code *Config} now
   * costs only an entry in {@link #SLOTS} + an optional named accessor.
   */
  public <T> ConfigRegistry with(final Class<T> slotType, final T replacement) {
    Objects.requireNonNull(slotType, "slotType");
    Objects.requireNonNull(replacement, "replacement");
    if (!slotType.isInstance(replacement)) {
      throw new ClassCastException(
          "replacement of type "
              + replacement.getClass().getName()
              + " is not a "
              + slotType.getName());
    }
    if (!slots.containsKey(slotType)) {
      throw new IllegalArgumentException(
          "Unknown config slot " + slotType.getName() + "; register it in ConfigRegistry.SLOTS");
    }
    final Map<Class<?>, Object> next = new LinkedHashMap<>(slots);
    next.put(slotType, replacement);
    return new ConfigRegistry(this.ships, next);
  }

  /**
   * Slot descriptor — one per typed sub-record this registry exposes. Pairs
   * the {@code *Config} {@link Class} (the slot key) with its
   * {@code DEFAULTS} sentinel (the value installed by {@link Builder}'s
   * default population so {@link ConfigRegistry#get} never returns null for
   * a registered slot).
   */
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

  /**
   * Mutable accumulator for a snapshot. Intended to be short-lived — populate
   * via the config layer, then call {@link #build()} to freeze. Not
   * thread-safe; build from one thread, publish via
   * {@link ConfigRegistrySystem#replace}.
   *
   * <p>Each {@code *Config} slot starts at its {@code DEFAULTS} sentinel
   * (see {@link ConfigRegistry#SLOTS}). Use {@link #with(Class, Object)} to
   * override a slot before {@link #build()}.
   */
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

    /**
     * Generic slot setter — write {@code value} into the slot keyed by
     * {@code slotType}. Replaces the per-slot named setters (one for each
     * of the 14 sub-records). Tests that need to override a slot before
     * {@link #build()} pass the config class as the key.
     */
    public <T> Builder with(final Class<T> slotType, final T value) {
      Objects.requireNonNull(slotType, "slotType");
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
