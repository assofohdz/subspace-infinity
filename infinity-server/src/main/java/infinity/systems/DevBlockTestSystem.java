// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import infinity.InfinityConstants;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev chat commands for poking at the mblock cell layer at runtime.
 * <ul>
 *   <li>{@code ~animTile} — writes an animated asteroid-small block at the
 *       caller's XZ, snapped to Y=2 (the visual tile layer above the world-Y=1
 *       collision plane). Proves the {@code g_Time}-driven shader animates
 *       cell geometry.
 * </ul>
 */
public class DevBlockTestSystem extends AbstractGameSystem {

  static final Logger log = LoggerFactory.getLogger(DevBlockTestSystem.class);

  private final Pattern animTileCommand = Pattern.compile("\\~animTile");

  private EntityData ed;
  private MapSystem mapSystem;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    mapSystem = getSystem(MapSystem.class);

    final ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);
    chat.registerPatternTriConsumer(
        animTileCommand,
        "~animTile — drop an animated asteroid-small block at your current position",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::dropAnimatedTile));
  }

  @Override
  protected void terminate() {
    // No EntitySets to release.
  }

  @Override
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String dropAnimatedTile(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    if (avatarEntityId == null) {
      return "~animTile: no avatar (spawn first)";
    }
    final BodyPosition bp = ed.getComponent(avatarEntityId, BodyPosition.class);
    if (bp == null) {
      return "~animTile: avatar has no BodyPosition";
    }
    final Vec3d here = bp.getLastLocation();
    // World tiles, prizes, ships all live at Y=1. Snap there so the block sits
    // in the same layer as the ship (collidable) and not floating above it.
    // Offset X by +2 so the block lands clearly in front of the ship and we don't
    // overwrite whatever cell the player is currently sitting on.
    final Vec3d target = new Vec3d(here.x + 2.0, 1.0, here.z);
    mapSystem.setCell(target, InfinityConstants.ANIMATED_ASTEROID_SMALL_BLOCK_TYPE);
    log.info("Placed animated asteroid-small at {}", target);
    return "~animTile: placed at " + target;
  }
}
