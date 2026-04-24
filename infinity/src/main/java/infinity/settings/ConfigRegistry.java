/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.settings;

import infinity.Ship;
import infinity.config.ShipConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Immutable per-arena config snapshot. Holds typed {@code *Config} records
 * produced by the config layer (Groovy script / legacy INI) and consumed by
 * spawn systems to seed per-entity components.
 *
 * <p>Snapshots are frozen at construction — the internal map is defensively
 * copied and wrapped unmodifiable. Live-reload installs a new snapshot via
 * {@link ConfigRegistrySystem#replace}; readers see either the old or new
 * snapshot, never a torn state.
 *
 * <p>MVP scope: ships only. Grow by adding per-type maps (bombs, bullets,
 * flags, ...) alongside {@link #ships} as their consumers land.
 */
public final class ConfigRegistry {

  /** Reusable empty snapshot. Returned for arenas with no loaded config. */
  public static final ConfigRegistry EMPTY = builder().build();

  private final Map<Ship, ShipConfig> ships;

  private ConfigRegistry(final EnumMap<Ship, ShipConfig> shipsSource) {
    final EnumMap<Ship, ShipConfig> copy = new EnumMap<>(Ship.class);
    copy.putAll(shipsSource);
    this.ships = Collections.unmodifiableMap(copy);
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

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Mutable accumulator for a snapshot. Intended to be short-lived — populate
   * via the config layer, then call {@link #build()} to freeze. Not
   * thread-safe; build from one thread, publish via
   * {@link ConfigRegistrySystem#replace}.
   */
  public static final class Builder {

    private final EnumMap<Ship, ShipConfig> ships = new EnumMap<>(Ship.class);

    public Builder ship(final Ship type, final ShipConfig config) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(config, "config");
      ships.put(type, config);
      return this;
    }

    public ConfigRegistry build() {
      return new ConfigRegistry(ships);
    }
  }
}
