// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.PortalConfig;

/**
 * Typed Groovy adapter for {@code portal.groovy} fragments. Parses a
 * {@code portal { … }} block into a {@link PortalConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * portal {
 *     activeTime  6000   // [Misc] WarpPointDelay  (centiseconds → ms ×10)
 * }
 * }</pre>
 */
public final class PortalAdapter
    extends SingleClosureAdapter<PortalConfig, PortalAdapter.PortalBuilder> {

  /** Stateless; safe to share across calls. */
  public static final PortalAdapter INSTANCE = new PortalAdapter();

  private PortalAdapter() {
    super("portal", PortalConfig.DEFAULTS);
  }

  @Override
  protected PortalBuilder newBuilder() {
    return new PortalBuilder();
  }

  @Override
  public PortalConfig extract(final PortalBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code portal { ... }} block. */
  public static final class PortalBuilder {

    private long activeTimeMs = PortalConfig.DEFAULTS.activeTimeMs();

    PortalBuilder() {}

    /**
     * {@code [Misc] WarpPointDelay} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void activeTime(final int centiseconds) {
      this.activeTimeMs = Validators.centisecondsToMs("portal.activeTime", centiseconds);
    }

    PortalConfig build() {
      return new PortalConfig(activeTimeMs);
    }
  }
}
