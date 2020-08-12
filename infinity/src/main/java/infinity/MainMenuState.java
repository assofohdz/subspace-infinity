package infinity;

/*
 * $Id$
 * 
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import com.simsilica.event.EventBus;
import com.simsilica.lemur.ActionButton;
import com.simsilica.lemur.CallMethodAction;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.OptionPanelState;
import com.simsilica.lemur.style.ElementId;
import com.simsilica.state.CommandConsoleState;

import infinity.client.ClientEvent;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;

/**
 *
 *
 *  @author    Paul Speed
 */
public class MainMenuState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(MainMenuState.class);

    private Node menuRoot = new Node("menuRoot");
    private Container mainWindow;
    private Node originalGuiNode;
 
    private ConnectionState connection;
    private HostState host;
    
    public MainMenuState() {
    }
 
    public Node getMenuRoot() {
        return menuRoot;
    }
 
    public Container getWindow() {
        return mainWindow;
    }
 
    /**
     *  Returns the camera size unscaled by getStandardScale(). 
     */
    public Vector2f getScreenSize() {
        int width = getApplication().getCamera().getWidth();        
        int height = getApplication().getCamera().getHeight();
        float standardScale = getStandardScale();        
        return new Vector2f(width / standardScale, height / standardScale);
    }
    
    /**
     *  Returns a normalized scaling used for the menu GUIs.  This will
     *  automatically scale down if the screen is a lower resolution but will
     *  not scale up beyond the 'ideal size'... in this case height = 720.
     */
    public float getStandardScale() {
        int height = getApplication().getCamera().getHeight();               
        return Math.min(1, height / 720f);
    }
    
    public void showError( String title, String error ) {
        getState(OptionPanelState.class).show(title, error);    
    }
 
    protected void connect() {
        log.info("Connect");
    }
    
    protected void host() {
        log.info("Host");
    }

    protected void newSinglePlayer() {
        log.info("newSinglePlayer");
        
        try {
 
            // Create a hosting state that we will connect to directly
            this.host = new HostState(8969, "private server", true);
            getStateManager().attach(host);
 
            // Add our listener that we can use to clean up the host state when
            // the client is disconnected
            EventBus.addListener(this, ClientEvent.clientConnected);
            EventBus.addListener(this, ClientEvent.clientDisconnected);
 
            // Now connect           
            this.connection = new ConnectionState(this, "127.0.0.1", 8969, true);         
        
            getStateManager().attach(connection);            
            
            // Disable ourselves
            setEnabled(false);
        } catch( RuntimeException e ) {
            log.error("Error setting up single player host/connection", e);
            String message = "Error setting up loopback on port:" + 8969;
            Throwable cause = e.getCause();
            if( cause != null ) {
                message += "\n" + cause.getClass().getSimpleName() + ":" + cause.getMessage();
            }
            showError("Hosting", message);
        }        
    }

    protected void onClientConnected( ClientEvent event ) {
    
        log.info("onClientConnected(" + event + ")");
    
        //getStateManager().attach(new ChatState());
        getStateManager().attach(new GameSessionState());
        
        // Can we get a leaf?
        //com.simsilica.mworld.net.client.WorldClientService world = 

    }

    protected void onClientDisconnected( ClientEvent event ) {
        if( host != null ) {
            // Clean the hosting state up since there is no
            // more connection.  The connection state cleans itself up
            getStateManager().detach(host);
            host = null;
            connection = null;
        }
    
        EventBus.removeListener(this, ClientEvent.clientDisconnected);
    }
 
    protected void options() {
        log.info("Options");
    }
    
    protected void exitGame() {
        log.info("Exit game");
        getApplication().stop();
    }
    
    @Override   
    protected void initialize( Application app ) {

        mainWindow = new Container(new ElementId("window.container"));
 
        Label title = mainWindow.addChild(new Label("Network Demo"));
        title.setInsets(new Insets3f(10, 10, 0, 10));
 
        Container singlePanel = mainWindow.addChild(new Container());
        singlePanel.setInsets(new Insets3f(10, 10, 10, 10));        
        singlePanel.addChild(new Label("Single Player", new ElementId("title")));
        singlePanel.addChild(new ActionButton(new CallMethodAction("New Game...", this, "newSinglePlayer")));  
        
        Container multiPanel = mainWindow.addChild(new Container());        
        multiPanel.setInsets(new Insets3f(10, 10, 10, 10));        
        multiPanel.addChild(new Label("Multiplayer", new ElementId("title")));
        multiPanel.addChild(new ActionButton(new CallMethodAction("Connect...", this, "connect")));  
        multiPanel.addChild(new ActionButton(new CallMethodAction("Host...", this, "host")));  
        
        ActionButton options = mainWindow.addChild(new ActionButton(new CallMethodAction(this, "options")));  
        options.setInsets(new Insets3f(5, 10, 5, 10));        
        
        ActionButton exit = mainWindow.addChild(new ActionButton(new CallMethodAction("Exit Game", this, "exitGame")));  
        exit.setInsets(new Insets3f(5, 10, 10, 10));        
           
        // Calculate a standard scale and position from the app's camera
        // height
        Vector3f pref = mainWindow.getPreferredSize().clone();        
        float standardScale = getStandardScale();
        menuRoot.setLocalScale(standardScale, standardScale, 1);
        Vector2f screenSize = getScreenSize();
        
        // With a slight bias toward the top        
        float y = screenSize.y * 0.6f + pref.y * 0.5f;
                                     
        mainWindow.setLocalTranslation(screenSize.x * 0.128f, y, 0);

        // As long the main menu state is attached, we will manage a
        // properly scaled GUI 
        Node gui = ((SimpleApplication)getApplication()).getGuiNode();
        gui.attachChild(menuRoot);
                        
        GuiGlobals globals = GuiGlobals.getInstance();
        originalGuiNode = globals.getPopupState().getGuiNode();        
        globals.getPopupState().setGuiNode(menuRoot);        
    }
 
    @Override   
    protected void cleanup( Application app ) {
        GuiGlobals globals = GuiGlobals.getInstance();
        globals.getPopupState().setGuiNode(originalGuiNode);
        menuRoot.removeFromParent();
    }
    
    @Override   
    protected void onEnable() { 
        menuRoot.attachChild(mainWindow);
        GuiGlobals.getInstance().requestCursorEnabled(this);
        GuiGlobals.getInstance().getFocusManagerState().setFocus(mainWindow);
        
        // While the main menu is enabled, we'll map the tilda key '~' (the 'grave' or back-tick) 
        // to the command console
        GuiGlobals.getInstance().getInputMapper().map(CommandConsoleState.F_OPEN_CONSOLE, KeyInput.KEY_GRAVE);
    }
    
    @Override   
    protected void onDisable() {
        GuiGlobals.getInstance().releaseCursorEnabled(this);
        mainWindow.removeFromParent();        
        GuiGlobals.getInstance().getInputMapper().removeMapping(CommandConsoleState.F_OPEN_CONSOLE, KeyInput.KEY_GRAVE);
    }
}
