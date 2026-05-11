// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mworld.TileId;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
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
 * Resolves a world position to an arena-relative grid label (e.g. {@code A5}, {@code AF12}). An
 * arena is one {@link TileId} (1024x1024 tiles); it subdivides into a 32x32 grid whose cells line
 * up with MOSS's leaf paging, so "which region" reuses the partitioning MOSS already tracks for
 * cell activity.
 *
 * <p>Column labels are spreadsheet-style: 0→A, 25→Z, 26→AA, 31→AF. Row labels are 1-indexed. The
 * center of a fresh arena is {@code P17}.
 */
public class RegionSystem extends BaseInfinitySystem {

  static final Logger log = LoggerFactory.getLogger(RegionSystem.class);

  private static final int LEAF = InfinityConstants.GRID_CELL_SIZE;
  private static final int REGIONS_PER_ARENA = InfinityConstants.TILE_SIZE / LEAF; // 32

  private final Pattern whereCommand = Pattern.compile("\\~where");
  private EntityData ed;
  private EntitySet arenaEntities;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);

    final ChatHostedPoster chat = requireSystem(InfinityChatHostedService.class);
    chat.registerPatternTriConsumer(
        whereCommand,
        "~where reports the arena and grid region you are in (e.g. trench.lvl A5)",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::commandWhere));
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
    // Nothing to do
  }

  @Override
  public void stop() {
    // Nothing to do
  }

  /**
   * Arena-relative grid label for a world position. Uses {@link TileId#fromWorld} so the result is
   * always relative to the containing tile, regardless of whether that tile hosts a loaded arena.
   */
  public String getRegionLabel(final Vec3d pos) {
    final TileId tile = TileId.fromWorld(pos);
    final Vec3i origin = tile.getWorld(null);
    final int col = clampIndex((int) Math.floor((pos.x - origin.x) / LEAF));
    final int row = clampIndex((int) Math.floor((pos.z - origin.z) / LEAF));
    return columnLabel(col) + (row + 1);
  }

  /** Spreadsheet-style: 0→A, 25→Z, 26→AA, 31→AF. Package-private for unit tests. */
  static String columnLabel(final int index) {
    final StringBuilder sb = new StringBuilder();
    int n = index;
    while (true) {
      sb.insert(0, (char) ('A' + (n % 26)));
      n = n / 26 - 1;
      if (n < 0) {
        break;
      }
    }
    return sb.toString();
  }

  private static int clampIndex(final int v) {
    if (v < 0) {
      return 0;
    }
    if (v >= REGIONS_PER_ARENA) {
      return REGIONS_PER_ARENA - 1;
    }
    return v;
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String commandWhere(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    if (avatarEntityId == null) {
      return "You have no avatar.";
    }
    final BodyPosition bp = ed.getComponent(avatarEntityId, BodyPosition.class);
    if (bp == null) {
      return "Cannot resolve your position.";
    }
    final Vec3d pos = bp.getLastLocation();
    if (pos == null) {
      return "Cannot resolve your position.";
    }

    final String label = getRegionLabel(pos);
    final String arenaName = findContainingArena(pos);
    return arenaName != null ? arenaName + " " + label : "No arena here (region " + label + ")";
  }

  private String findContainingArena(final Vec3d pos) {
    arenaEntities.applyChanges();
    for (final Entity arena : arenaEntities) {
      final ArenaMap map = arena.get(ArenaMap.class);
      final Vec3d min = map.getMin();
      final Vec3d max = map.getMax();
      if (pos.x >= min.x && pos.x < max.x && pos.z >= min.z && pos.z < max.z) {
        return arena.get(ArenaId.class).getArena();
      }
    }
    return null;
  }
}
