// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.BrickConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code brick.groovy} fragments. Parses a
 * {@code brick { … }} block into a {@link BrickConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * brick {
 *     span  7      // [Brick] BrickSpan (wall length in tiles)
 *     time  1000   // [Brick] BrickTime (centiseconds → ms ×10)
 * }
 * }</pre>
 */
public final class BrickAdapter
    implements GroovySettingsAdapter<BrickConfig, BrickAdapter.BrickBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BrickAdapter INSTANCE = new BrickAdapter();

  private BrickAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public BrickBuilder bind(final Binding binding) {
    final BrickBuilder builder = new BrickBuilder();
    binding.setVariable("brick", new BrickClosure(builder));
    return builder;
  }

  @Override
  public BrickConfig extract(final BrickBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public BrickConfig empty() {
    return BrickConfig.DEFAULTS;
  }

  /** Bound to the {@code brick} variable in the script. */
  private static final class BrickClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final BrickBuilder builder;

    BrickClosure(final BrickBuilder builder) {
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

  /** Delegate for the {@code brick { ... }} block. */
  public static final class BrickBuilder {

    private int spanTiles = BrickConfig.DEFAULTS.spanTiles();
    private long timeMs = BrickConfig.DEFAULTS.timeMs();

    BrickBuilder() {}

    /** {@code [Brick] BrickSpan} — wall length in tiles. */
    public void span(final int value) {
      this.spanTiles = value;
    }

    /**
     * {@code [Brick] BrickTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void time(final int centiseconds) {
      this.timeMs = centiseconds * 10L;
    }

    BrickConfig build() {
      return new BrickConfig(spanTiles, timeMs);
    }
  }
}
