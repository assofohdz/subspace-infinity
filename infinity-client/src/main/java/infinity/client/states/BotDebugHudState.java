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
    this.hud.addChild(new Label("Bots  (w: * = selectable behaviour)"))
        .setInsets(new Insets3f(2, 6, 2, 6));
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
    final int height = getApplication().getCamera().getHeight();
    // Lower-left: clear of the top-anchored ECS bin-debug HUD, and left-anchored so long rows
    // (the nav:/weights tail of each line) stay on-screen instead of running off the right edge.
    this.hud.setLocalTranslation(10f, height * 0.42f, 100f);
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
    final String ship = debug.shipType().isEmpty() ? "?" : titleCase(debug.shipType());
    final String tgt =
        debug.targetId() < 0
            ? "none"
            : debug.targetId() + (debug.clockHour() == 0 ? "" : " @" + debug.clockHour() + "h");
    // objective/role appended to the ship tag once populated (#06).
    final String objRole =
        debug.objectiveName().isEmpty() ? "" : " · " + debug.objectiveName() + "/" + debug.roleName();
    // Line 1: identity + what it's doing. Line 2: how (BT leaf + steering) + why (weights).
    return String.format(
        "Bot %s [%s%s]  goal:%s  tgt:%s  nav:%s%n"
            + "  bt:%s  turn%+.2f thr%+.2f  w:{%s}",
        bot.getId().getId(),
        ship,
        objRole,
        debug.currentGoalLabel(),
        tgt,
        debug.navMode(),
        debug.branch(),
        debug.intentTurn(),
        debug.intentThrust(),
        debug.topScores());
  }

  /** {@code WARBIRD} → {@code Warbird} for a tidier ship tag. */
  private static String titleCase(final String name) {
    return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
  }
}
