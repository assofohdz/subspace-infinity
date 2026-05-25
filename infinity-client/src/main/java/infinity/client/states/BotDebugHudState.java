// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import infinity.client.ConnectionState;
import infinity.es.BotDebug;
import infinity.es.ship.BotShip;
import java.util.HashMap;
import java.util.Map;

/**
 * Top-right HUD listing every bot in the arena with its current brain state — branch,
 * pursue target, intent vector, and clock-hour direction to target ({@code 12} = ahead,
 * {@code 3} = right). Data sourced from the wire-crossing {@link BotDebug} component
 * written each tick by {@code BotBrainSystem}; see ADR-0009.
 */
public class BotDebugHudState extends BaseAppState {

  private EntitySet bots;
  private Container hud;
  private final Map<EntityId, Label> rowLabels = new HashMap<>();

  @Override
  protected void initialize(final Application app) {
    final EntityData ed = getState(ConnectionState.class).getEntityData();
    this.bots = ed.getEntities(BotShip.class, BotDebug.class);
    this.hud = new Container();
    this.hud.addChild(new Label("Bots")).setInsets(new Insets3f(2, 6, 2, 6));
  }

  @Override
  protected void cleanup(final Application app) {
    if (this.bots != null) {
      this.bots.release();
      this.bots = null;
    }
    this.rowLabels.clear();
  }

  @Override
  protected void onEnable() {
    final Node gui = ((SimpleApplication) getApplication()).getGuiNode();
    final int width = getApplication().getCamera().getWidth();
    final int height = getApplication().getCamera().getHeight();
    // Top-right, 300px wide column.
    this.hud.setLocalTranslation(width - 310f, height - 10f, 100f);
    gui.attachChild(this.hud);
  }

  @Override
  protected void onDisable() {
    this.hud.removeFromParent();
  }

  @Override
  public void update(final float tpf) {
    if (this.bots == null) {
      return;
    }
    this.bots.applyChanges();
    for (final Entity removed : this.bots.getRemovedEntities()) {
      final Label label = this.rowLabels.remove(removed.getId());
      if (label != null) {
        this.hud.removeChild(label);
      }
    }
    for (final Entity added : this.bots.getAddedEntities()) {
      final Label label = this.hud.addChild(new Label(""));
      label.setInsets(new Insets3f(1, 6, 1, 6));
      this.rowLabels.put(added.getId(), label);
    }
    for (final Entity entity : this.bots) {
      this.rowLabels.get(entity.getId()).setText(formatRow(entity));
    }
  }

  private static String formatRow(final Entity bot) {
    final BotDebug debug = bot.get(BotDebug.class);
    final String targetStr = debug.targetId() < 0 ? "none" : Long.toString(debug.targetId());
    final String clockStr = debug.clockHour() == 0 ? "-" : debug.clockHour() + "h";
    // objective/role bracket only when populated (lands with #06).
    final String objRole =
        debug.objectiveName().isEmpty()
            ? ""
            : String.format(" [%s/%s]", debug.objectiveName(), debug.roleName());
    return String.format(
        "Bot %s%s  %s  tgt=%s @%s  turn=%+.2f thrust=%+.2f%n"
            + "  Goal:%s  Nav:%s  W:{%s}  top:%s",
        bot.getId().getId(),
        objRole,
        debug.branch(),
        targetStr,
        clockStr,
        debug.intentTurn(),
        debug.intentThrust(),
        debug.currentGoalLabel(),
        debug.navMode(),
        debug.topScores(),
        debug.weightBreakdown());
  }
}
