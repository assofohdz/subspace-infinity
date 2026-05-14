// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.test;

import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import sun.misc.Unsafe;

/**
 * Headless {@link GuiGlobals} bring-up / tear-down for lifecycle tests
 * that exercise states reaching {@code GuiGlobals.getInstance()}.
 *
 * <p>Strategy: real {@code GuiGlobals.initialize(app)} against a
 * {@link SyntheticApplication} built with {@link SyntheticApplication.Builder#headlessForGuiGlobals()}.
 * Lemur's init checks {@code app.getContext().getType() == Headless} and
 * takes a fast-path that only needs the asset manager + state manager,
 * skipping mouse / focus / picking app states (so {@code GuiGlobals.inputMapper}
 * is null on that path). Tests that touch the input mapper —
 * {@link com.simsilica.lemur.GuiGlobals#getInputMapper()} —
 * get a stub {@link InputMapper} reflectively allocated via {@link Unsafe}
 * (skipping its {@code InputManager}-requiring ctor) with its
 * {@code listenerMap} pre-seeded so {@code addDelegate} / {@code removeDelegate}
 * survive without NPE. Tear-down nulls the static singleton via reflection —
 * Lemur exposes no {@code clear()} — so each test gets a fresh instance.
 */
@SuppressWarnings("PMD.ClassNamingConventions")
public final class GuiGlobalsTestFixture {

  private GuiGlobalsTestFixture() { /* static helpers */ }

  /**
   * Initialize {@link GuiGlobals} against {@code app} and install a stub
   * {@link InputMapper} so {@code PlayerListState}-style initializers
   * (which call {@code addDelegate}) survive. Idempotent if the singleton
   * already exists (e.g. consecutive tests in the same JVM).
   */
  public static void install(final SyntheticApplication app) {
    if (GuiGlobals.getInstance() == null) {
      GuiGlobals.initialize(app);
    }
    installStubInputMapper();
  }

  /**
   * Reflectively clears the static {@code GuiGlobals.instance} so the next
   * test gets a clean slate. JUnit's per-test instance isolation does not
   * cover JVM-static singletons.
   */
  public static void uninstall() {
    try {
      final Field instance = GuiGlobals.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(null, null);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("Failed to clear GuiGlobals singleton", e);
    }
  }

  /**
   * Allocate an {@link InputMapper} via {@link Unsafe} (skip its ctor —
   * the real ctor requires an {@link com.jme3.input.InputManager} which
   * isn't available headless), seed only the field
   * ({@code listenerMap}) that {@code addDelegate}/{@code removeDelegate}
   * dereference, and install it into the {@code GuiGlobals.inputMapper}
   * slot.
   */
  private static void installStubInputMapper() {
    try {
      final Unsafe unsafe = unsafe();
      final InputMapper stub = (InputMapper) unsafe.allocateInstance(InputMapper.class);
      final Field listenerMap = InputMapper.class.getDeclaredField("listenerMap");
      listenerMap.setAccessible(true);
      listenerMap.set(stub, new HashMap<FunctionId, Object>());
      final Field guiInputMapper = GuiGlobals.class.getDeclaredField("inputMapper");
      guiInputMapper.setAccessible(true);
      guiInputMapper.set(GuiGlobals.getInstance(), stub);
    } catch (final ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to install stub InputMapper", e);
    }
  }

  private static Unsafe unsafe() throws ReflectiveOperationException {
    final Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return (Unsafe) f.get(null);
  }
}
