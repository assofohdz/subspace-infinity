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

/** Chat-command face over {@link ArenaSystem}: {@code ~loadMap}, {@code ~unloadMap}, {@code ~swapMap}, {@code ~loadArena}, {@code ~arenas}. Register after {@link ArenaSystem}. */
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
  }

  @Override
  public void stop() {
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String loadArenaByNameCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    return arenaSystem.loadArena(matcher.group(1));
  }

  /** {@code ~arenas} — lists loaded arenas, marks the avatar's arena with {@code [you]}. */
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

  /** {@code ~loadMap <mapFile>} — map's basename is the arena name. */
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

  /** {@code ~swapMap <arena> <newMap>} — preserves arena identity + settings; delegates to {@link ArenaSystem#swapArenaMap}. */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String swapArenaCommand(
      final EntityId id, final EntityId avatarEntityId, final Matcher matcher) {
    return arenaSystem.swapArenaMap(matcher.group(1), matcher.group(2));
  }

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
