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

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.server.AssetLoaderService;
import infinity.settings.GroovyFragmentLoader;
import infinity.settings.IniLoader;
import infinity.settings.SSSLoader;
import infinity.settings.SettingListener;
import infinity.sim.util.InfinityRunTimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private AssetLoaderService assetLoader;
  private final GroovyFragmentLoader groovyFragmentLoader = new GroovyFragmentLoader();


  public void addListener(final SettingListener listener) {
    listeners.add(listener);
  }

  public void removeListener(final SettingListener listener) {
    listeners.remove(listener);
  }

  @Override
  protected void initialize() {

    this.assetLoader = getSystem(AssetLoaderService.class);

    assetLoader.registerLoader(IniLoader.class, "ini");
    assetLoader.registerLoader(IniLoader.class, "cfg");
    assetLoader.registerLoader(IniLoader.class, "conf");

    assetLoader.registerLoader(SSSLoader.class, "sss");
    assetLoader.registerLoader(SSSLoader.class, "set");

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
   * Load each {@code classpathPath} as an INI fragment (with the existing
   * {@link IniLoader} {@code #include} support) and merge them into a single
   * {@link Ini} stored under {@code arenaName}. Used by the Groovy arena-load
   * path: {@code arena.groovy}'s typed core fields (map / shipsScript / spawn)
   * live in {@link infinity.config.ArenaConfig}, so the {@code Ini} cached
   * here only carries the included-fragment data (the still-INI
   * {@code conf/<preset>/*.conf} preset content) for downstream
   * {@link #getString} / {@link #getInt} lookups.
   *
   * @param arenaName arena identity (folder name under {@code arenas/})
   * @param classpathPaths fragment paths in declaration order; later fragments
   *     overwrite earlier ones on key conflict (last-wins, matching
   *     {@code #include}'s semantics)
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

  private Ini loadFragmentIni(final String classpathPath) {
    if (classpathPath == null || classpathPath.isBlank()) {
      return null;
    }
    // Dispatch on extension so Groovy fragments and INI fragments share one
    // includeFragment surface in arena.groovy. Groovy fragments produce an
    // ini4j Ini with the same shape (sections + string values) as IniLoader,
    // so the merge / getInt / getString path downstream is identical.
    if (classpathPath.endsWith(".groovy")) {
      return groovyFragmentLoader.load(classpathPath);
    }
    // The asset loader's keys are leading-slashless; arena.groovy paths are
    // typically classpath-absolute with a leading slash, so strip it.
    final String key =
        classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
    try {
      return (Ini) assetLoader.loadAsset(key);
    } catch (final Exception e) {
      log.warn("Fragment {} failed to load: {}", classpathPath, e.getMessage());
      return null;
    }
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
