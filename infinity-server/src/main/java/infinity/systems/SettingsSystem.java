// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.settings.GroovyFragmentLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.ini4j.Profile.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-arena {@link Ini} store for legacy INI-routed settings reads. Slated for deletion as the typed pipeline absorbs each section. */
public class SettingsSystem extends AbstractGameSystem {

  static Logger log = LoggerFactory.getLogger(SettingsSystem.class);
  private final Map<String, Profile> arenaSettingsMap = new HashMap<>();
  private final GroovyFragmentLoader groovyFragmentLoader = new GroovyFragmentLoader();

  @Override
  protected void initialize() {
  }

  @Override
  protected void terminate() {
  }

  @Override
  public void update(final SimTime tpf) {
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  /** Merges fragments under {@code arenaName}; later fragments win on key conflict (matches {@code include} semantics). */
  public void loadFragments(final String arenaName, final List<String> classpathPaths) {
    final Profile merged = new Ini();
    if (classpathPaths != null) {
      for (final String path : classpathPaths) {
        final Profile fragment = loadFragmentIni(path);
        if (fragment == null) {
          log.warn("Fragment {} not loadable for arena {}", path, arenaName);
          continue;
        }
        mergeInto(merged, fragment);
      }
    }
    arenaSettingsMap.put(arenaName, merged);
  }

  public void reloadFragments(final ArenaId arenaId, final List<String> classpathPaths) {
    loadFragments(arenaId.getArena(), classpathPaths);
  }

  private Profile loadFragmentIni(final String classpathPath) {
    if (classpathPath == null || classpathPath.isBlank()) {
      return null;
    }
    if (!classpathPath.endsWith(".groovy")) {
      log.warn(
          "Fragment {} is not a .groovy file; only Groovy fragments are supported",
          classpathPath);
      return null;
    }
    return groovyFragmentLoader.load(classpathPath);
  }

  private static void mergeInto(final Profile target, final Profile source) {
    for (final String sectionName : source.keySet()) {
      final Section src = source.get(sectionName);
      Section dst = target.get(sectionName);
      if (dst == null) {
        dst = target.add(sectionName);
      }
      for (final String k : src.keySet()) {
        // ini4j Section.put returns the previous value; ignore — last-wins is the
        // documented merge contract.
        dst.put(k, src.get(k));
      }
    }
  }

  public Profile getIni(final String arenaName) {
    return arenaSettingsMap.get(arenaName);
  }

  /**
   * Raw string accessor. Returns {@code defaultValue} if the arena has no settings loaded, or the
   * section or key is absent.
   */
  public String getString(
      final String arenaBaseName,
      final String section,
      final String key,
      final String defaultValue) {
    final String raw = rawValue(arenaBaseName, section, key);
    return raw != null ? raw : defaultValue;
  }

  /**
   * Integer accessor with bool-alias fallback. Accepts numeric strings and, failing that, the
   * Subspace bool aliases ({@code Y/Yes/True/On/1} → 1, {@code N/No/False/Off/0} → 0). Returns
   * {@code defaultValue} if the key is missing or unparseable.
   */
  public int getInt(
      final String arenaBaseName, final String section, final String key, final int defaultValue) {
    final String raw = rawValue(arenaBaseName, section, key);
    if (raw == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (final NumberFormatException ignored) {
      final Boolean aliased = parseBoolAlias(raw);
      return aliased != null ? (aliased ? 1 : 0) : defaultValue;
    }
  }

  /**
   * Boolean accessor accepting the Subspace bool aliases ({@code Y/Yes/True/On/1} and {@code
   * N/No/False/Off/0}), case-insensitive. Returns {@code defaultValue} if missing or unparseable.
   */
  public boolean getBool(
      final String arenaBaseName,
      final String section,
      final String key,
      final boolean defaultValue) {
    final String raw = rawValue(arenaBaseName, section, key);
    if (raw == null) {
      return defaultValue;
    }
    final Boolean aliased = parseBoolAlias(raw);
    return aliased != null ? aliased : defaultValue;
  }

  /**
   * Enum accessor. Case-insensitive match against the enum constants; falls back to {@code
   * defaultValue} if the key is missing or does not match. {@code defaultValue} must be non-null
   * (it also carries the enum type).
   */
  public <T extends Enum<T>> T getEnum(
      final String arenaBaseName, final String section, final String key, final T defaultValue) {
    final String raw = rawValue(arenaBaseName, section, key);
    if (raw == null) {
      return defaultValue;
    }
    final String trimmed = raw.trim();
    for (final T constant : defaultValue.getDeclaringClass().getEnumConstants()) {
      if (constant.name().equalsIgnoreCase(trimmed)) {
        return constant;
      }
    }
    return defaultValue;
  }

  private String rawValue(final String arenaBaseName, final String section, final String key) {
    final Profile ini = arenaSettingsMap.get(arenaBaseName);
    if (ini == null) {
      return null;
    }
    final Section sec = ini.get(section);
    return sec != null ? sec.get(key) : null;
  }

  private static Boolean parseBoolAlias(final String raw) {
    final String v = raw.trim();
    if (isTrueAlias(v)) {
      return Boolean.TRUE;
    }
    if (isFalseAlias(v)) {
      return Boolean.FALSE;
    }
    return null;
  }

  /** True iff {@code v} is one of the truthy boolean aliases (y/yes/true/on/1). */
  private static boolean isTrueAlias(final String v) {
    return v.equalsIgnoreCase("y")
        || v.equalsIgnoreCase("yes")
        || v.equalsIgnoreCase("true")
        || v.equalsIgnoreCase("on")
        || v.equals("1");
  }

  /** True iff {@code v} is one of the falsy boolean aliases (n/no/false/off/0). */
  private static boolean isFalseAlias(final String v) {
    return v.equalsIgnoreCase("n")
        || v.equalsIgnoreCase("no")
        || v.equalsIgnoreCase("false")
        || v.equalsIgnoreCase("off")
        || v.equals("0");
  }

}
