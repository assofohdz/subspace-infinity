// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.PortalConfig;
import java.util.List;

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
    implements GroovySettingsAdapter<PortalConfig, PortalAdapter.PortalBuilder> {

  /** Stateless; safe to share across calls. */
  public static final PortalAdapter INSTANCE = new PortalAdapter();

  private PortalAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public PortalBuilder bind(final Binding binding) {
    final PortalBuilder builder = new PortalBuilder();
    binding.setVariable("portal", new PortalClosure(builder));
    return builder;
  }

  @Override
  public PortalConfig extract(final PortalBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public PortalConfig empty() {
    return PortalConfig.DEFAULTS;
  }

  /** Bound to the {@code portal} variable in the script. */
  private static final class PortalClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final PortalBuilder builder;

    PortalClosure(final PortalBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(builder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
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
      this.activeTimeMs = centiseconds * 10L;
    }

    PortalConfig build() {
      return new PortalConfig(activeTimeMs);
    }
  }
}
