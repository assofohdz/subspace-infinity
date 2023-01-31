/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.*;
import com.jme3.scene.*;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.style.ElementId;
import infinity.Main;
import infinity.MainMenuState;
import infinity.client.ConnectionState;

/**
 *  A basic "login" state that provides a simple UI for logging in
 *  once a connection has been established.  This just manages the UI
 *  and calls back to the ConnectionState to do the actual 'work'.
 *
 *  @author    Paul Speed
 */
public class LoginState extends BaseAppState {

  private Container loginPanel;
  private TextField nameField;

  private Container serverInfoPanel;
  private String serverInfo;

  public LoginState( String serverInfo ) {
    this.serverInfo = serverInfo;
  }

  protected void join() {

    String name = nameField.getText().trim();
    if( getState(ConnectionState.class).join(nameField.getText()) ) {
      getStateManager().detach(this);
    }
  }

  protected void cancel() {
    getState(ConnectionState.class).disconnect();
    getStateManager().detach(this);
  }

  @Override
  protected void initialize( Application app ) {
    loginPanel = new Container();
    loginPanel.addChild(new Label("Login", new ElementId("title")));

    Container props = loginPanel.addChild(new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.Last)));
    props.setBackground(null);
    props.addChild(new Label("Name:"));
    nameField = props.addChild(new TextField(System.getProperty("user.name")), 1);

    Container buttons = loginPanel.addChild(new Container(new SpringGridLayout(Axis.X, Axis.Y)));
    buttons.setBackground(null);
    buttons.setLayout(new SpringGridLayout(Axis.X, Axis.Y));
    buttons.addChild(new ActionButton(new CallMethodAction("Join", this, "join")));
    buttons.addChild(new ActionButton(new CallMethodAction("Cancel", this, "cancel")));

    float scale = 1.5f * getState(MainMenuState.class).getStandardScale();
    loginPanel.setLocalScale(scale);

    Vector3f prefs = loginPanel.getPreferredSize().clone();
    prefs.x = Math.max(300, prefs.x);
    loginPanel.setPreferredSize(prefs.clone());

    // Now account for scaling
    prefs.multLocal(scale);

    int width = app.getCamera().getWidth();
    int height = app.getCamera().getHeight();

    loginPanel.setLocalTranslation(width * 0.5f - prefs.x * 0.5f, height * 0.5f + prefs.y * 0.5f, 10);

    serverInfoPanel = new Container();
    serverInfoPanel.setLocalScale(scale);
    serverInfoPanel.addChild(new Label("Server Description", new ElementId("title")));
    Label desc = serverInfoPanel.addChild(new Label(serverInfo));
    desc.setInsets(new Insets3f(5, 15, 5, 15)); // should leave this up to the style really
    desc.setTextHAlignment(HAlignment.Center);

    Vector3f prefs2 = serverInfoPanel.getPreferredSize().mult(scale);
    serverInfoPanel.setLocalTranslation(width * 0.5f - prefs2.x * 0.5f,
        loginPanel.getLocalTranslation().y - prefs.y - 20 * scale,
        10);

  }

  @Override
  protected void cleanup( Application app ) {
  }

  @Override
  protected void onEnable() {
    Node root = ((Main)getApplication()).getGuiNode();
    root.attachChild(loginPanel);
    root.attachChild(serverInfoPanel);
    GuiGlobals.getInstance().requestFocus(loginPanel);

    // And kill the cursor
    GuiGlobals.getInstance().setCursorEventsEnabled(true);

    // A 'bug' in Lemur causes it to miss turning the cursor off if
    // we are enabled before the MouseAppState is initialized.
    getApplication().getInputManager().setCursorVisible(true);
  }

  @Override
  protected void onDisable() {
    loginPanel.removeFromParent();
    serverInfoPanel.removeFromParent();

    // And kill the cursor
    GuiGlobals.getInstance().setCursorEventsEnabled(false);

    // A 'bug' in Lemur causes it to miss turning the cursor off if
    // we are enabled before the MouseAppState is initialized.
    getApplication().getInputManager().setCursorVisible(false);

  }
}
