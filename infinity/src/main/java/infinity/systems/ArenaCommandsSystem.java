// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.config.ArenaConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imperative chat-command face over {@link ArenaSystem}'s declarative core.
 *
 * <p>Split out from {@link ArenaSystem} to keep the lifecycle / hot-reload /
 * lookup core under PMD's class-level cyclomatic-complexity threshold —
 * mirrors the {@code ChecksShipsSystem} / {@code ChecksWorldSystem} pattern
 * from earlier rounds. Command handlers stay short delegates that read state
 * via {@link ArenaSystem}'s public accessors and forward mutating ops back
 * to its public API ({@link ArenaSystem#loadArena}, {@link
 * ArenaSystem#setDesired}, {@link ArenaSystem#reconcile}, {@link
 * ArenaSystem#swapArenaMap}).
 *
 * <p>Hosted commands:
 *
 * <ul>
 *   <li>{@code ~loadMap <mapFile>} — load the arena whose name is the map's
 *       base filename (current naming convention).
 *   <li>{@code ~unloadMap <mapFile>} — resolve the map back to its arena
 *       and flip {@code desired=false}.
 *   <li>{@code ~swapMap <arenaName> <newMap>} — replace the map of a loaded
 *       arena in place; entity + name + settings preserved.
 *   <li>{@code ~loadArena <arenaName>} — load by arena folder name.
 *   <li>{@code ~arenas} — list every loaded arena with bounds + centre,
 *       marking the arena containing the caller's avatar with {@code [you]}.
 * </ul>
 *
 * <p>Initialization order: {@link ArenaSystem} must be registered ahead of
 * this system (lookup happens in {@link #initialize}). This is wired in
 * {@code GameServer.start} where the two systems are registered adjacent.
 *
 * @author Asser Fahrenholz
 */
public class ArenaCommandsSystem extends BaseInfinitySystem {

  static final Logger log = LoggerFactory.getLogger(ArenaCommandsSystem.class);

  private final Pattern loadMap = Pattern.compile("\\~loadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadMap = Pattern.compile("\\~unloadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern swapMap =
      Pattern.compile("\\~swapMap\\s([\\w()\\-]+)\\s+(\\w+\\.(?:lvl|lvz))");
  private final Pattern loadArenaByName = Pattern.compile("\\~loadArena\\s([\\w()\\-]+)");
  private final Pattern listArenas = Pattern.compile("\\~arenas");

  private EntityData ed;
  private EntitySet arenaEntities;
  private ArenaSystem arenaSystem;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    arenaSystem = requireSystem(ArenaSystem.class);
    // Per-system EntitySet — avoids reaching into ArenaSystem's private
    // arenaEntities and lets this system update independently.
    arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);

    final ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);
    chat.registerPatternTriConsumer(
        loadMap,
        "The command to load a new map is ~loadMap <mapName>, where <mapName> is the name "
            + "of the map you want to load",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::loadArenaByMapCommand));
    chat.registerPatternTriConsumer(
        unloadMap,
        "The command to unload a new map is ~unloadMap <mapName>, where <mapName> is the "
            + "name of the map you want to unload",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::unloadArenaByMapCommand));
    chat.registerPatternTriConsumer(
        swapMap,
        "Swap the map of a loaded arena: ~swapMap <arenaName> <newMap>. The arena's identity and "
            + "settings stay the same; only the underlying map is replaced.",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::swapArenaCommand));
    chat.registerPatternTriConsumer(
        loadArenaByName,
        "The command to load an arena by name is ~loadArena <arenaName>. Reads "
            + "arenas/<arenaName>/arena.conf, loads the map declared by its [General] Map= key "
            + "(falling back to <arenaName>.lvl), and attaches the settings.",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::loadArenaByNameCommand));
    chat.registerPatternTriConsumer(
        listArenas,
        "~arenas lists every loaded arena with its world bounds + centre. Marks the "
            + "arena containing your avatar with [you].",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::listArenasCommand));
  }

  @Override
  protected void terminate() {
    arenaEntities.release();
    arenaEntities = null;
  }

  @Override
  public void update(final SimTime tpf) {
    arenaEntities.applyChanges();
  }

  @Override
  public void start() {
    // No-op.
  }

  @Override
  public void stop() {
    // No-op.
  }

  /* ---------------------------------------------------------------- */
  /* Command handlers — imperative face over declarative core         */
  /* ---------------------------------------------------------------- */

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String loadArenaByNameCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    return arenaSystem.loadArena(matcher.group(1));
  }

  /**
   * {@code ~arenas} — list every loaded arena with its world bounds + centre,
   * marking the arena that contains the player's avatar with {@code [you]}.
   * Helps operators navigate a multi-arena zone (e.g. {@code testarena} +
   * {@code trench} + {@code deva} all autoLoaded).
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String listArenasCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    arenaEntities.applyChanges();
    if (arenaEntities.isEmpty()) {
      return "No arenas loaded.";
    }

    Vec3d avatarPos = null;
    if (avatarEntityId != null) {
      final BodyPosition bp = ed.getComponent(avatarEntityId, BodyPosition.class);
      if (bp != null) {
        avatarPos = bp.getLastLocation();
      }
    }

    final StringBuilder out = new StringBuilder();
    out.append("Loaded arenas (").append(arenaEntities.size()).append("):\n");
    for (final Entity arena : arenaEntities) {
      final ArenaId arenaId = arena.get(ArenaId.class);
      final ArenaMap map = arena.get(ArenaMap.class);
      final Vec3d min = map.getMin();
      final Vec3d max = map.getMax();
      final boolean youAreHere =
          avatarPos != null
              && avatarPos.x >= min.x
              && avatarPos.x < max.x
              && avatarPos.z >= min.z
              && avatarPos.z < max.z;
      out.append("  ")
          .append(arenaId.getArena())
          .append(" — bounds=(")
          .append((int) min.x).append(',').append((int) min.z)
          .append(")..(")
          .append((int) max.x).append(',').append((int) max.z)
          .append(") centre=(")
          .append((int) ((min.x + max.x) / 2)).append(',').append((int) ((min.z + max.z) / 2))
          .append(')');
      if (youAreHere) {
        out.append(" [you]");
      }
      out.append('\n');
    }
    return out.toString().trim();
  }

  /**
   * {@code ~loadMap <mapFile>} — the map's base name is used as the arena name, per current
   * convention.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String loadArenaByMapCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = mapFile.substring(0, mapFile.lastIndexOf('.'));
    return arenaSystem.loadArena(arenaName);
  }

  /** {@code ~unloadMap <mapFile>} — resolves to its owning arena and flips desired=false. */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String unloadArenaByMapCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = findArenaByMap(mapFile);
    if (arenaName == null) {
      return "No arena currently loaded with map " + mapFile;
    }
    arenaSystem.setDesired(arenaName, false);
    arenaSystem.reconcile(arenaName);
    return describe(arenaName);
  }

  /**
   * {@code ~swapMap <arenaName> <newMap>} — replace the map of a loaded arena in place. The arena
   * entity, name, and settings are preserved; only the underlying map cells change. Delegates to
   * {@link ArenaSystem#swapArenaMap} which owns the registry-mutating logic.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String swapArenaCommand(
      final EntityId id, final EntityId avatarEntityId, final Matcher matcher) {
    return arenaSystem.swapArenaMap(matcher.group(1), matcher.group(2));
  }

  /**
   * Render a one-line state description for {@code arenaName}. Reads
   * {@link ArenaSystem.ArenaState} via {@link ArenaSystem#getArenaState} and
   * formats the case-by-case message.
   */
  private String describe(final String arenaName) {
    final ArenaSystem.ArenaState state = arenaSystem.getArenaState(arenaName);
    if (state == null) {
      return "Arena " + arenaName + " not in registry";
    }
    switch (state) {
      case LOADED:
        return "Arena " + arenaName + " loaded";
      case LOADING:
        return "Arena " + arenaName + " loading";
      case UNLOADING:
        return "Arena " + arenaName + " unloading";
      case NOT_LOADED:
        return "Arena " + arenaName + " not loaded";
      case FAILED:
        return "Arena " + arenaName + " failed: " + arenaSystem.getArenaError(arenaName);
      default:
        return "Arena " + arenaName + " state=" + state;
    }
  }

  /** Scan loaded arenas for one whose current map file equals {@code mapFile}. */
  private String findArenaByMap(final String mapFile) {
    for (final String name : arenaSystem.getActiveArenas()) {
      final ArenaConfig cfg = arenaSystem.getArenaConfig(name);
      if (mapFile.equals(cfg.mapFile())) {
        return name;
      }
    }
    return null;
  }
}
