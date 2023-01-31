/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity;

import com.simsilica.sim.GameSystemManager;
import infinity.client.ConnectionState;
import infinity.server.GameServer;
import java.io.*;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.network.ConnectionListener;
import com.jme3.network.HostedConnection;
import com.jme3.network.Server;
import com.jme3.scene.Node;

import com.simsilica.lemur.*;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.style.ElementId;
import com.simsilica.state.DebugHudState;
import com.simsilica.state.DebugHudState.Location;

/**
 *  Manages the game server when hosting a game.
 *
 *  @author    Paul Speed
 */
public class HostState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(HostState.class);

    private GameServer gameServer;
    private int port;

    private VersionedHolder<String> hostingState;
    private VersionedHolder<String> connectionCount;

    private ConnectionListener connectionListener = new ConnectionObserver();

    private Container hostWindow;
    private GameSystemManager systems;

    public HostState( int port, String description ) {
        try {
            this.port = port;
            this.gameServer = new GameServer(port, description);
            systems = gameServer.getSystems();
            gameServer.getServer().addConnectionListener(connectionListener);
        } catch( IOException e ) {
            throw new RuntimeException("Error creating server", e);
        }
    }
    
    public GameServer getGameServer() {
        return gameServer;
    }

    public GameSystemManager getSystems() {
        return systems;
    }
    protected void joinGame() {
        log.info("joinGame()");
        getStateManager().attach(new ConnectionState(this, "127.0.0.1", port));
        setEnabled(false); // hide our window
    }

    protected void stopHosting() {
        log.info("stopHosting()");
        if( gameServer.getServer().isRunning() && !gameServer.getServer().getConnections().isEmpty() ) {
            String msg = "Really kick all " + gameServer.getServer().getConnections().size() + " connections?";
            getState(OptionPanelState.class).show("Disconnect", msg,
                new CallMethodAction("Yes", this, "detach"),
                new EmptyAction("No"),
                new EmptyAction("Cancel"));
        } else {
            // Just detach
            detach();
        }
    }

    protected void detach() {
        getStateManager().detach(this);
    }

    @Override
    protected void initialize( Application app ) {
        // We'll manage the server itself as part of the app state
        // lifecycle so that we can use enabled state for GUI elements
        // if we want.  Plus, the server will not be reusable once closed
        // and so neither will the state.  That fits better with the detach()
        // lifecycle than it does with enabled/disabled.
        gameServer.start();

        hostingState = getState(DebugHudState.class).createDebugValue("Hosting", Location.Right);
        hostingState.setObject("Online");

        connectionCount = getState(DebugHudState.class).createDebugValue("Connections", Location.Right);
        resetConnectionCount();

        hostWindow = new Container();

        // For now just something simple
        hostWindow.addChild(new Label("Hosting Control", new ElementId("title")));
        hostWindow.addChild(new ActionButton(new CallMethodAction("Join Game", this, "joinGame")));
        hostWindow.addChild(new ActionButton(new CallMethodAction("Stop Hosting", this, "stopHosting")));
    }

    @Override
    protected void cleanup( Application app ) {
        gameServer.close("Shutting down.");
        hostingState.setObject("Offline");

        // And remove the debug messages anyway
        getState(DebugHudState.class).removeDebugValue("Hosting");
        getState(DebugHudState.class).removeDebugValue("Connections");

        // And re-enable the main menu
        getState(MainMenuState.class).setEnabled(true);
    }

    @Override
    protected void onEnable() {
        Node gui = ((Main)getApplication()).getGuiNode();

        int height = getApplication().getCamera().getHeight();
        hostWindow.setLocalTranslation(10, height - 10, 0);
        gui.attachChild(hostWindow);
        GuiGlobals.getInstance().requestFocus(hostWindow);

        // And kill the cursor
        GuiGlobals.getInstance().setCursorEventsEnabled(true);

        // A 'bug' in Lemur causes it to miss turning the cursor off if
        // we are enabled before the MouseAppState is initialized.
        getApplication().getInputManager().setCursorVisible(true);
    }

    @Override
    protected void onDisable() {
        hostWindow.removeFromParent();

        // And kill the cursor
        GuiGlobals.getInstance().setCursorEventsEnabled(false);

        // A 'bug' in Lemur causes it to miss turning the cursor off if
        // we are enabled before the MouseAppState is initialized.
        getApplication().getInputManager().setCursorVisible(false);
    }

    protected void resetConnectionCount() {
        connectionCount.setObject(String.valueOf(gameServer.getServer().getConnections().size()));
    }

    private class ConnectionObserver implements ConnectionListener {

        public void connectionAdded( Server server, HostedConnection conn ) {
            resetConnectionCount();
        }

        public void connectionRemoved( Server server, HostedConnection conn ) {
            resetConnectionCount();
        }
    }

}
