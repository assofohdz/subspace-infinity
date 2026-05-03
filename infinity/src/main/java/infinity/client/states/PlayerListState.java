// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import java.util.Comparator;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.style.ElementId;

import infinity.client.MainGameFunctions;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.Frequency;
import infinity.es.ship.Player;

/**
 * Displays the list of players currently known to the client (those with a Player, Name, and
 * Frequency component). Toggled with F2 via {@link MainGameFunctions#F_PLAYER_LIST}.
 *
 * @author Asser Fahrenholz
 */
public class PlayerListState extends BaseAppState {

  private EntityData ed;
  private Container playerWindow;
  private Container playerRows;
  private PlayerContainer players;
  private boolean dirty;

  public PlayerListState() {
    setEnabled(false);
  }

  public void toggleEnabled() {
    setEnabled(!isEnabled());
  }

  @Override
  protected void initialize(final Application app) {
    ed = getState(ConnectionState.class).getEntityData();

    playerWindow = new Container();
    final Label title = playerWindow.addChild(new Label("Players", new ElementId("title")));
    title.setInsets(new Insets3f(2, 2, 0, 2));

    final Container sub = playerWindow.addChild(new Container());
    sub.setInsets(new Insets3f(10, 10, 10, 10));
    playerRows = sub.addChild(new Container());

    players = new PlayerContainer(ed);

    final InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
    inputMapper.addDelegate(MainGameFunctions.F_PLAYER_LIST, this, "toggleEnabled");
  }

  @Override
  protected void cleanup(final Application app) {
    final InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
    inputMapper.removeDelegate(MainGameFunctions.F_PLAYER_LIST, this, "toggleEnabled");
  }

  @Override
  protected void onEnable() {
    players.start();
    dirty = true;
    rebuildRows();

    final Node gui = ((SimpleApplication) getApplication()).getGuiNode();
    final int width = getApplication().getCamera().getWidth();
    final int height = getApplication().getCamera().getHeight();
    final Vector3f pref = playerWindow.getPreferredSize();
    playerWindow.setLocalTranslation(width - pref.x - 10, height * 0.5f + pref.y * 0.5f, 100);
    gui.attachChild(playerWindow);
  }

  @Override
  protected void onDisable() {
    playerWindow.removeFromParent();
    players.stop();
  }

  @Override
  public void update(final float tpf) {
    if (players.update() || dirty) {
      rebuildRows();
    }
  }

  private void rebuildRows() {
    dirty = false;
    playerRows.clearChildren();

    final PlayerEntry[] entries = players.getArray().clone();
    java.util.Arrays.sort(entries, PlayerEntry.BY_FREQ_THEN_NAME);

    final EntityId avatarId = getState(GameSessionState.class).getAvatarEntityId();
    int lastFreq = Integer.MIN_VALUE;

    for (final PlayerEntry entry : entries) {
      if (entry.name == null) {
        continue;
      }
      if (entry.frequency != lastFreq) {
        lastFreq = entry.frequency;
        playerRows.addChild(
            new Label("Freq " + entry.frequency, new ElementId("help.key.label")));
        playerRows.addChild(new Label("", new ElementId("help.description.label")),
            Integer.valueOf(1));
      }
      final String displayName =
          avatarId != null && entry.entityId.equals(avatarId) ? "> " + entry.name : "  " + entry.name;
      playerRows.addChild(new Label(displayName, new ElementId("help.description.label")));
      playerRows.addChild(new Label("", new ElementId("help.key.label")), Integer.valueOf(1));
    }

    if (playerWindow.getParent() != null) {
      final int width = getApplication().getCamera().getWidth();
      final int height = getApplication().getCamera().getHeight();
      final Vector3f pref = playerWindow.getPreferredSize();
      playerWindow.setLocalTranslation(width - pref.x - 10, height * 0.5f + pref.y * 0.5f, 100);
    }
  }

  private final class PlayerEntry {
    static final Comparator<PlayerEntry> BY_FREQ_THEN_NAME =
        Comparator.<PlayerEntry>comparingInt(e -> e.frequency)
            .thenComparing(e -> e.name == null ? "" : e.name);

    final EntityId entityId;
    String name;
    int frequency;

    PlayerEntry(final Entity e) {
      this.entityId = e.getId();
      update(e);
    }

    void update(final Entity e) {
      this.name = e.get(Name.class) == null ? null : e.get(Name.class).getName();
      this.frequency = e.get(Frequency.class).getFrequency();
    }
  }

  private class PlayerContainer extends EntityContainer<PlayerEntry> {
    PlayerContainer(final EntityData ed) {
      super(ed, Player.class, Name.class, Frequency.class);
    }

    @Override
    protected PlayerEntry addObject(final Entity e) {
      dirty = true;
      return new PlayerEntry(e);
    }

    @Override
    protected void updateObject(final PlayerEntry object, final Entity e) {
      object.update(e);
      dirty = true;
    }

    @Override
    protected void removeObject(final PlayerEntry object, final Entity e) {
      dirty = true;
    }

    @Override
    public PlayerEntry[] getArray() {
      return super.getArray();
    }
  }
}
