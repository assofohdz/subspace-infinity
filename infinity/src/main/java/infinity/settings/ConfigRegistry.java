// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.Ship;
import infinity.config.BombConfig;
import infinity.config.BulletConfig;
import infinity.config.BurstFireConfig;
import infinity.config.GravBombConfig;
import infinity.config.MineConfig;
import infinity.config.PrizeConfig;
import infinity.config.PrizeWeightsConfig;
import infinity.config.RepelConfig;
import infinity.config.RocketConfig;
import infinity.config.ShipConfig;
import infinity.config.ThorConfig;
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
 * <p>Each weapon-projectile sub-record sits as a direct slot here (post-B1a
 * flatten) — no intermediate {@code WeaponsConfig} grouping struct. Per
 * {@code .scratch/settings-pipeline.md}'s target architecture: the public
 * API is the visual inventory of "what's tunable per arena."
 */
public final class ConfigRegistry {

  /** Reusable empty snapshot. Returned for arenas with no loaded config. */
  public static final ConfigRegistry EMPTY = builder().build();

  private final Map<Ship, ShipConfig> ships;
  private final BulletConfig bullet;
  private final BombConfig bomb;
  private final GravBombConfig gravBomb;
  private final MineConfig mine;
  private final BurstFireConfig burst;
  private final RepelConfig repel;
  private final RocketConfig rocket;
  private final ThorConfig thor;
  private final PrizeConfig prize;
  private final PrizeWeightsConfig prizeWeights;

  private ConfigRegistry(
      final EnumMap<Ship, ShipConfig> shipsSource,
      final BulletConfig bullet,
      final BombConfig bomb,
      final GravBombConfig gravBomb,
      final MineConfig mine,
      final BurstFireConfig burst,
      final RepelConfig repel,
      final RocketConfig rocket,
      final ThorConfig thor,
      final PrizeConfig prize,
      final PrizeWeightsConfig prizeWeights) {
    final EnumMap<Ship, ShipConfig> copy = new EnumMap<>(Ship.class);
    copy.putAll(shipsSource);
    this.ships = Collections.unmodifiableMap(copy);
    this.bullet = bullet;
    this.bomb = bomb;
    this.gravBomb = gravBomb;
    this.mine = mine;
    this.burst = burst;
    this.repel = repel;
    this.rocket = rocket;
    this.thor = thor;
    this.prize = prize;
    this.prizeWeights = prizeWeights;
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

  /** Per-arena gun-bullet tuning. Never {@code null} (defaults to {@link BulletConfig#DEFAULTS}). */
  public BulletConfig bullet() { return bullet; }

  /** Per-arena bomb tuning. Never {@code null} (defaults to {@link BombConfig#DEFAULTS}). */
  public BombConfig bomb() { return bomb; }

  /** Per-arena gravity-bomb / wormhole tuning. Never {@code null} (defaults to {@link GravBombConfig#DEFAULTS}). */
  public GravBombConfig gravBomb() { return gravBomb; }

  /** Per-arena mine tuning. Never {@code null} (defaults to {@link MineConfig#DEFAULTS}). */
  public MineConfig mine() { return mine; }

  /** Per-arena burst-firing tuning. Never {@code null} (defaults to {@link BurstFireConfig#DEFAULTS}). */
  public BurstFireConfig burst() { return burst; }

  /** Per-arena Repel-effect tuning. Never {@code null} (defaults to {@link RepelConfig#DEFAULTS}). */
  public RepelConfig repel() { return repel; }

  /** Per-arena Rocket-buff tuning ({@code RocketThrust}/{@code RocketSpeed}). Never {@code null} (defaults to {@link RocketConfig#DEFAULTS}). */
  public RocketConfig rocket() { return rocket; }

  /** Per-arena Thor projectile tuning. Never {@code null} (defaults to {@link ThorConfig#DEFAULTS}). */
  public ThorConfig thor() { return thor; }

  /**
   * Per-arena prize-spawn defaults (decay / max count / bounty value).
   * Never {@code null} — the builder defaults to {@link PrizeConfig#DEFAULTS}.
   */
  public PrizeConfig prize() {
    return prize;
  }

  /**
   * Per-arena prize-spawn weight table — name→weight map, one entry per
   * Subspace prize type. Never {@code null}; defaults to
   * {@link PrizeWeightsConfig#DEFAULTS} (empty map = no prizes spawn).
   * Populated by {@link PrizeWeightsAdapter} from the typed
   * {@code prize-weights.groovy} fragment.
   */
  public PrizeWeightsConfig prizeWeights() {
    return prizeWeights;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Return a copy of this snapshot with {@link #bullet} replaced. */
  public ConfigRegistry withBullet(final BulletConfig replacement) {
    Objects.requireNonNull(replacement, "bullet");
    return copyWith(replacement, bomb, gravBomb, mine, burst, repel, rocket, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #bomb} replaced. */
  public ConfigRegistry withBomb(final BombConfig replacement) {
    Objects.requireNonNull(replacement, "bomb");
    return copyWith(bullet, replacement, gravBomb, mine, burst, repel, rocket, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #gravBomb} replaced. */
  public ConfigRegistry withGravBomb(final GravBombConfig replacement) {
    Objects.requireNonNull(replacement, "gravBomb");
    return copyWith(bullet, bomb, replacement, mine, burst, repel, rocket, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #mine} replaced. */
  public ConfigRegistry withMine(final MineConfig replacement) {
    Objects.requireNonNull(replacement, "mine");
    return copyWith(bullet, bomb, gravBomb, replacement, burst, repel, rocket, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #burst} replaced. */
  public ConfigRegistry withBurst(final BurstFireConfig replacement) {
    Objects.requireNonNull(replacement, "burst");
    return copyWith(bullet, bomb, gravBomb, mine, replacement, repel, rocket, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #repel} replaced. */
  public ConfigRegistry withRepel(final RepelConfig replacement) {
    Objects.requireNonNull(replacement, "repel");
    return copyWith(bullet, bomb, gravBomb, mine, burst, replacement, rocket, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #rocket} replaced. */
  public ConfigRegistry withRocket(final RocketConfig replacement) {
    Objects.requireNonNull(replacement, "rocket");
    return copyWith(bullet, bomb, gravBomb, mine, burst, repel, replacement, thor, prize, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #thor} replaced. */
  public ConfigRegistry withThor(final ThorConfig replacement) {
    Objects.requireNonNull(replacement, "thor");
    return copyWith(bullet, bomb, gravBomb, mine, burst, repel, rocket, replacement, prize, prizeWeights);
  }

  /** Counterpart to the weapon-section withers for the {@code [Prize]} fragment section. */
  public ConfigRegistry withPrize(final PrizeConfig replacement) {
    Objects.requireNonNull(replacement, "prize");
    return copyWith(bullet, bomb, gravBomb, mine, burst, repel, rocket, thor, replacement, prizeWeights);
  }

  /** Return a copy of this snapshot with {@link #prizeWeights} replaced. */
  public ConfigRegistry withPrizeWeights(final PrizeWeightsConfig replacement) {
    Objects.requireNonNull(replacement, "prizeWeights");
    return copyWith(bullet, bomb, gravBomb, mine, burst, repel, rocket, thor, prize, replacement);
  }

  private ConfigRegistry copyWith(
      final BulletConfig bullet,
      final BombConfig bomb,
      final GravBombConfig gravBomb,
      final MineConfig mine,
      final BurstFireConfig burst,
      final RepelConfig repel,
      final RocketConfig rocket,
      final ThorConfig thor,
      final PrizeConfig prize,
      final PrizeWeightsConfig prizeWeights) {
    final EnumMap<Ship, ShipConfig> source = new EnumMap<>(Ship.class);
    source.putAll(this.ships);
    return new ConfigRegistry(
        source, bullet, bomb, gravBomb, mine, burst, repel, rocket, thor, prize, prizeWeights);
  }

  /**
   * Mutable accumulator for a snapshot. Intended to be short-lived — populate
   * via the config layer, then call {@link #build()} to freeze. Not
   * thread-safe; build from one thread, publish via
   * {@link ConfigRegistrySystem#replace}.
   */
  public static final class Builder {

    private final EnumMap<Ship, ShipConfig> ships = new EnumMap<>(Ship.class);
    private BulletConfig bullet = BulletConfig.DEFAULTS;
    private BombConfig bomb = BombConfig.DEFAULTS;
    private GravBombConfig gravBomb = GravBombConfig.DEFAULTS;
    private MineConfig mine = MineConfig.DEFAULTS;
    private BurstFireConfig burst = BurstFireConfig.DEFAULTS;
    private RepelConfig repel = RepelConfig.DEFAULTS;
    private RocketConfig rocket = RocketConfig.DEFAULTS;
    private ThorConfig thor = ThorConfig.DEFAULTS;
    private PrizeConfig prize = PrizeConfig.DEFAULTS;
    private PrizeWeightsConfig prizeWeights = PrizeWeightsConfig.DEFAULTS;

    public Builder ship(final Ship type, final ShipConfig config) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(config, "config");
      ships.put(type, config);
      return this;
    }

    public Builder bullet(final BulletConfig bullet) {
      this.bullet = Objects.requireNonNull(bullet, "bullet");
      return this;
    }

    public Builder bomb(final BombConfig bomb) {
      this.bomb = Objects.requireNonNull(bomb, "bomb");
      return this;
    }

    public Builder gravBomb(final GravBombConfig gravBomb) {
      this.gravBomb = Objects.requireNonNull(gravBomb, "gravBomb");
      return this;
    }

    public Builder mine(final MineConfig mine) {
      this.mine = Objects.requireNonNull(mine, "mine");
      return this;
    }

    public Builder burst(final BurstFireConfig burst) {
      this.burst = Objects.requireNonNull(burst, "burst");
      return this;
    }

    public Builder repel(final RepelConfig repel) {
      this.repel = Objects.requireNonNull(repel, "repel");
      return this;
    }

    public Builder rocket(final RocketConfig rocket) {
      this.rocket = Objects.requireNonNull(rocket, "rocket");
      return this;
    }

    public Builder thor(final ThorConfig thor) {
      this.thor = Objects.requireNonNull(thor, "thor");
      return this;
    }

    public Builder prize(final PrizeConfig prize) {
      this.prize = Objects.requireNonNull(prize, "prize");
      return this;
    }

    public Builder prizeWeights(final PrizeWeightsConfig prizeWeights) {
      this.prizeWeights = Objects.requireNonNull(prizeWeights, "prizeWeights");
      return this;
    }

    public ConfigRegistry build() {
      return new ConfigRegistry(
          ships, bullet, bomb, gravBomb, mine, burst, repel, rocket, thor, prize, prizeWeights);
    }
  }
}
