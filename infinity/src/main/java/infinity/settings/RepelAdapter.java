// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.RepelConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code repel.groovy} fragments. Parses a
 * {@code repel { … }} block into a {@link RepelConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadRepel}.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * repel {
 *     speed     5000    // [Repel] RepelSpeed (raw Subspace velocity units)
 *     time      225     // [Repel] RepelTime (centiseconds → ms ×10)
 *     distance  512     // [Repel] RepelDistance (Subspace pixels)
 * }
 * }</pre>
 */
public final class RepelAdapter
    implements GroovySettingsAdapter<RepelConfig, RepelAdapter.RepelBuilder> {

  /** Stateless; safe to share across calls. */
  public static final RepelAdapter INSTANCE = new RepelAdapter();

  private RepelAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public RepelBuilder bind(final Binding binding) {
    final RepelBuilder builder = new RepelBuilder();
    binding.setVariable("repel", new RepelClosure(builder));
    return builder;
  }

  @Override
  public RepelConfig extract(final RepelBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public RepelConfig empty() {
    return RepelConfig.DEFAULTS;
  }

  /** Bound to the {@code repel} variable in the script. */
  private static final class RepelClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final RepelBuilder builder;

    RepelClosure(final RepelBuilder builder) {
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

  /** Delegate for the {@code repel { ... }} block. */
  public static final class RepelBuilder {

    private int speed = RepelConfig.DEFAULTS.speed();
    private long timeMs = RepelConfig.DEFAULTS.timeMs();
    private int distancePixels = RepelConfig.DEFAULTS.distancePixels();

    RepelBuilder() {}

    /** {@code [Repel] RepelSpeed} — repulsion speed applied to entities in range. */
    public void speed(final int value) {
      this.speed = value;
    }

    /**
     * {@code [Repel] RepelTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void time(final int centiseconds) {
      this.timeMs = centiseconds * 10L;
    }

    /** {@code [Repel] RepelDistance} — effect radius in Subspace pixels. */
    public void distance(final int value) {
      this.distancePixels = value;
    }

    RepelConfig build() {
      return new RepelConfig(speed, timeMs, distancePixels);
    }
  }
}
