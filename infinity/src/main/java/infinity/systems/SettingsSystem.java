// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.settings.GroovyFragmentLoader;
import infinity.settings.SettingListener;
import infinity.sim.util.InfinityRunTimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This state loads the settings for all arenas and notifies all listeners of the new settings.
 * Listeners are meant to keep a local copy of the settings so as not to reference this state every
 * time they have use for a setting
 *
 * <p>Every setting will be stored per ship in the entitydata, but this state will load the settings
 * and can update ship settings when requested (for example when a ship enters a new arena)
 *
 * @author Asser Fahrenholz
 */
public class SettingsSystem extends AbstractGameSystem {

  static Logger log = LoggerFactory.getLogger(SettingsSystem.class);
  private final HashMap<String, Ini> arenaSettingsMap = new HashMap<>();
  ArrayList<SettingListener> listeners = new ArrayList<>();
  private final GroovyFragmentLoader groovyFragmentLoader = new GroovyFragmentLoader();


  public void addListener(final SettingListener listener) {
    listeners.add(listener);
  }

  public void removeListener(final SettingListener listener) {
    listeners.remove(listener);
  }

  @Override
  protected void initialize() {
    // Fragment loading is now Groovy-only; the asset-loader registration that
    // used to bind .ini/.cfg/.conf to IniLoader is gone, since arena.groovy's
    // includeFragment paths only ever resolve to .groovy files now.
  }

  @Override
  protected void terminate() {
    // Nothing to do
  }

  @Override
  public void update(final SimTime tpf) {

  }

  @Override
  public void start() {
    // Nothing to do here
  }

  @Override
  public void stop() {
    // Nothing to do here
  }

  /**
   * Called when some setting change and listeners has to update their local copy of the settings.
   *
   * @param arenaId the arena to lookup settings for
   * @param section the section of the setting
   * @param setting the setting to retrieve
   */
  private void settingChanged(final ArenaId arenaId, final String section, final String setting) {
    for (final SettingListener listener : listeners) {
      listener.arenaSettingsChange(arenaId, section, setting);
    }
  }

  /**
   * Load each {@code classpathPath} as a Groovy fragment (with
   * {@link GroovyFragmentLoader}'s recursive {@code include} support) and merge
   * them into a single {@link Ini} stored under {@code arenaName}. Used by the
   * Groovy arena-load path: {@code arena.groovy}'s typed core fields (map /
   * shipsScript / spawn) live in {@link infinity.config.ArenaConfig}, so the
   * {@code Ini} cached here only carries the included-fragment data (the
   * preset {@code .groovy} files under {@code infinity/zone/conf/}) for
   * downstream {@link #getString} / {@link #getInt} lookups.
   *
   * @param arenaName arena identity (folder name under {@code arenas/})
   * @param classpathPaths fragment paths in declaration order; later fragments
   *     overwrite earlier ones on key conflict (last-wins, matching
   *     {@code include}'s semantics)
   */
  public void loadFragments(final String arenaName, final List<String> classpathPaths) {
    final Ini merged = new Ini();
    if (classpathPaths != null) {
      for (final String path : classpathPaths) {
        final Ini fragment = loadFragmentIni(path);
        if (fragment == null) {
          log.warn("Fragment {} not loadable for arena {}", path, arenaName);
          continue;
        }
        mergeInto(merged, fragment);
      }
    }
    arenaSettingsMap.put(arenaName, merged);
  }

  /**
   * Reload the arena's merged settings from {@code classpathPaths} and fire
   * {@link SettingListener#arenaSettingsChange} for every {@code (section, key)}
   * whose effective value changed. Used by {@code ArenaSystem}'s file watcher
   * when a Groovy fragment is edited at runtime, so listeners with cached
   * tuning stay in sync without a server restart.
   *
   * <p>The diff is taken against the pre-reload merged {@link Ini}, not against
   * the individual fragment that changed — that handles the case where a key
   * in fragment A is shadowed by fragment B (only B's reload should produce a
   * "changed" event for that key). No-op-equivalent reloads (mtime bump
   * without value changes) fire no events.
   *
   * @param arenaId identifies the arena (its {@code arenaName} keys the merged
   *     store; the {@link ArenaId} is forwarded to listeners verbatim)
   * @param classpathPaths fragment paths to load, in declaration order; same
   *     contract as {@link #loadFragments}
   */
  public void reloadFragments(final ArenaId arenaId, final List<String> classpathPaths) {
    final String arenaName = arenaId.getArena();
    final Ini oldIni = arenaSettingsMap.get(arenaName);
    loadFragments(arenaName, classpathPaths);
    final Ini newIni = arenaSettingsMap.get(arenaName);
    fireChangeDiff(arenaId, oldIni, newIni);
  }

  private void fireChangeDiff(final ArenaId arenaId, final Ini oldIni, final Ini newIni) {
    final Set<String> sectionNames = new HashSet<>();
    if (oldIni != null) {
      sectionNames.addAll(oldIni.keySet());
    }
    if (newIni != null) {
      sectionNames.addAll(newIni.keySet());
    }
    for (final String sectionName : sectionNames) {
      final Section oldSec = oldIni == null ? null : oldIni.get(sectionName);
      final Section newSec = newIni == null ? null : newIni.get(sectionName);
      final Set<String> keyNames = new HashSet<>();
      if (oldSec != null) {
        keyNames.addAll(oldSec.keySet());
      }
      if (newSec != null) {
        keyNames.addAll(newSec.keySet());
      }
      for (final String key : keyNames) {
        final String oldV = oldSec == null ? null : oldSec.get(key);
        final String newV = newSec == null ? null : newSec.get(key);
        if (!Objects.equals(oldV, newV)) {
          settingChanged(arenaId, sectionName, key);
        }
      }
    }
  }

  private Ini loadFragmentIni(final String classpathPath) {
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

  private static void mergeInto(final Ini target, final Ini source) {
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

  /**
   * Update a single setting for the given arena and notify listeners. The caller must pass the
   * arena's {@link ArenaId} component so listeners can filter on arena identity.
   *
   * @param arenaId the arena whose settings to mutate
   * @param section INI section name (e.g. {@code "Bomb"})
   * @param setting key within the section (e.g. {@code "BombDamageLevel"})
   * @param value new value; written as a string, consumers coerce as needed
   * @return the previous value under that key, or {@code null} if none
   */
  public String setSetting(
      final ArenaId arenaId, final String section, final String setting, final String value) {
    final String arenaName = arenaId.getArena();
    final Ini ini = arenaSettingsMap.get(arenaName);
    if (ini == null) {
      throw new InfinityRunTimeException("No settings loaded for arena " + arenaName);
    }
    Section sec = ini.get(section);
    if (sec == null) {
      sec = ini.add(section);
    }
    final String previous = sec.get(setting);
    sec.put(setting, value);
    settingChanged(arenaId, section, setting);
    return previous;
  }

  public Ini getIni(final String arenaName) {
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
    final Ini ini = arenaSettingsMap.get(arenaBaseName);
    if (ini == null) {
      return null;
    }
    final Section sec = ini.get(section);
    return sec != null ? sec.get(key) : null;
  }

  private static Boolean parseBoolAlias(final String raw) {
    final String v = raw.trim();
    if (v.equalsIgnoreCase("y")
        || v.equalsIgnoreCase("yes")
        || v.equalsIgnoreCase("true")
        || v.equalsIgnoreCase("on")
        || v.equals("1")) {
      return Boolean.TRUE;
    }
    if (v.equalsIgnoreCase("n")
        || v.equalsIgnoreCase("no")
        || v.equalsIgnoreCase("false")
        || v.equalsIgnoreCase("off")
        || v.equals("0")) {
      return Boolean.FALSE;
    }
    return null;
  }

}
