// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.World;
import com.simsilica.mworld.db.ColumnDb;
import com.simsilica.sim.SimTime;
import infinity.server.DefaultColumnDb;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandTriFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorldSystem extends BaseInfinitySystem {

  private final Pattern editCell = Pattern.compile("\\~editCell\\s(\\d+)\\s(\\d+)");
  private DefaultColumnDb colDb;
  private World world;

  public WorldSystem() {
  }

  public ColumnDb getColumnDb() {
    return colDb;
  }

  @Override
  public void start() {
  }

  @Override
  public void update(SimTime time) {
  }

  @Override
  public void stop() {
  }

  @Override
  protected void initialize() {
    world = requireSystem(World.class);

    InfinityChatHostedService chat = requireSystem(InfinityChatHostedService.class);

    chat.registerPatternTriConsumer(
        editCell,
        "The command to flip a worldcell is ~editCell <x> <y> <z>, where x,y and z are the world coords",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::flipWorldCell));
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String flipWorldCell(EntityId player, EntityId avatarId, Matcher matcher) {
    String x = matcher.group(1);
    String z = matcher.group(2);

    Vec3d pos = new Vec3d(Double.parseDouble(x), 1, Double.parseDouble(z));

    int cellType = world.getWorldCell(pos);
    if (cellType == 0) {
      cellType = 10;
    } else {
      cellType = 0;
    }
    world.setWorldCell(pos, cellType);

    return "Flipped cell at " + pos;
  }

  public int setWorldCell(Vec3d pos, int cellType) {
    return world.setWorldCell(pos, cellType);
  }

  public World getWorld() {
    return world;
  }

  @Override
  protected void terminate() {
  }
}
