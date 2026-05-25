// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import com.simsilica.es.EntityComponent;
import javax.annotation.Nullable;

/**
 * Server-only cache of a bot's derived {@link CapabilityProfile} (ADR-0014). Stamped by
 * {@code BotBrainSystem} at wiring time and re-derived when the arena config reloads; the planner
 * crosses it through the synergy table to behaviour weights. Lives in {@code infinity.ai.capability}
 * (not {@code infinity.es}) because it references {@link CapabilityProfile}, which {@code infinity.es}
 * may not depend on. Never synced — no serializer needed.
 */
public final class BotCapability implements EntityComponent {

  @Nullable private final CapabilityProfile profile;

  public BotCapability() {
    this(null);
  }

  public BotCapability(@Nullable final CapabilityProfile profile) {
    this.profile = profile;
  }

  @Nullable
  public CapabilityProfile profile() {
    return this.profile;
  }
}
