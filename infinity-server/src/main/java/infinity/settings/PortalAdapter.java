// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.PortalConfig;

/** Typed adapter for {@code portal {…}} → {@link PortalConfig}. REFERENCE.md §Misc {@code WarpPointDelay}. */
public final class PortalAdapter
    extends SingleClosureAdapter<PortalConfig, PortalAdapter.PortalBuilder> {

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

    /** {@code [Misc] WarpPointDelay} — centiseconds (×10 → ms). */
    public void activeTime(final int centiseconds) {
      this.activeTimeMs = Validators.centisecondsToMs("portal.activeTime", centiseconds);
    }

    PortalConfig build() {
      return new PortalConfig(activeTimeMs);
    }
  }
}
