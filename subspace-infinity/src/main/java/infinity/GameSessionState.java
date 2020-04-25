/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.math.*;

import com.simsilica.event.EventBus;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.state.CompositeAppState;

import com.simsilica.ethereal.TimeSource;

import com.simsilica.es.EntityId;
import com.simsilica.lemur.event.MouseAppState;
import infinity.client.audio.AudioState;
import infinity.client.audio.SIAudioFactory;

import infinity.debug.TimeSequenceState;
import infinity.net.GameSessionListener;
import infinity.net.client.GameSessionClientService;
import infinity.net.chat.ChatSessionListener;
import infinity.net.chat.client.ChatClientService;
import infinity.client.CameraState;
import infinity.client.FlagStateClient;
import infinity.client.MapStateClient;
import infinity.client.HudLabelState;
import infinity.client.LightState;
import infinity.client.view.ModelViewState;
import infinity.client.PlayerListState;
import infinity.client.PlayerMovementState;
import infinity.client.RadarStateTexture;
import infinity.client.ResourceStateClient;
import infinity.client.view.SISpatialFactory;
import infinity.client.ShipFrequencyStateClient;
import infinity.client.view.SkyState;
import infinity.client.view.SpaceGridState;

/**
 * The core state that manages the game session. This has several child app
 * states whose lifecycles are directly linked to this one.
 *
 * @author Paul Speed
 */
public class GameSessionState extends CompositeAppState {

    static Logger log = LoggerFactory.getLogger(GameSessionState.class);

    private GameSessionObserver gameSessionObserver = new GameSessionObserver();
    private ChatSessionObserver chatSessionObserver = new ChatSessionObserver();
    private ChatCommandEntry chatEntry = new ChatCommandEntry();

    // Temporary reference FIXME
    private PlayerMovementState us;

    private final EntityId shipId;

    public GameSessionState(EntityId shipId, TimeSource ts) {
        // add normal states on the super-constructor
        super(new MessageState(),
                new TimeState(ts), // Has to be before any visuals that might need it.
                new MapStateClient(),
                new SkyState(),
                new HudLabelState(shipId),
                new SpaceGridState(ServerGameConstants.GRID_CELL_SIZE, 10, new ColorRGBA(0.8f, 1f, 1f, 0.5f)),
                new ShipFrequencyStateClient(shipId),
                new FlagStateClient(shipId),
                new ResourceStateClient(shipId),
                new AudioState(new SIAudioFactory()),
                new LightState(),
                new ModelViewState(new SISpatialFactory(), shipId),
                new CameraState(shipId),
                new RadarStateTexture(),
                new PlayerMovementState(shipId)
        //new InfinityLightState()
        //new MapEditorState()
        );

        this.shipId = shipId;

        // Add states that need to support enable/disable independent of
        // the outer state using addChild().
        addChild(new InGameMenuState(false), true);
        addChild(new CommandConsoleState(), true);

        // For popping up a time sync debug panel
        //addChild(new TimeSequenceState(), true);
        addChild(new HelpState(), true);
        addChild(new PlayerListState(), true);

        System.out.println("created GameSessionState w. shipId: " + shipId);
    }

    public void disconnect() {
        // Remove ourselves
        getStateManager().detach(this);
    }

    @Override
    protected void initialize(Application app) {
        log.debug("++initialize()");
        super.initialize(app);

        System.out.println("initialize in GameSessionState");
        EventBus.publish(GameSessionEvent.sessionStarted, new GameSessionEvent());

        // Add a self-message because we're too late to have caught the
        // player joined message for ourselves.  (Please we'd want it to look like this, anyway.)
        getState(MessageState.class).addMessage("> You have joined the game.", ColorRGBA.Yellow);
        getState(ConnectionState.class).getService(GameSessionClientService.class).addGameSessionListener(gameSessionObserver);

        // Setup the chat related services
        getState(ConnectionState.class).getService(ChatClientService.class).addChatSessionListener(chatSessionObserver);
        getState(CommandConsoleState.class).setCommandEntry(chatEntry);

        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.activateGroup(MainGameFunctions.IN_GAME);

        addChild(new MouseAppState(app));
        log.debug("--initialize()");
    }

    @Override
    protected void cleanup(Application app) {
        log.debug("++cleanup()");
        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.deactivateGroup(MainGameFunctions.IN_GAME);

        EventBus.publish(GameSessionEvent.sessionEnded, new GameSessionEvent());

        // The below will fail because there is no message state anymore... so
        // it wouldn't show the message anyway.       
        // getState(MessageState.class).addMessage("> You have left the game.", ColorRGBA.Yellow);
        getChild(CommandConsoleState.class).setCommandEntry(null);

        super.cleanup(app);
        log.debug("--cleanup()");
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        //GuiGlobals.getInstance().setCursorEventsEnabled(false);
    }

    @Override
    protected void onDisable() {
        super.onEnable();
        //GuiGlobals.getInstance().setCursorEventsEnabled(true);
    }

    /**
     * Notified by the server about game-session related events.
     */
    private class GameSessionObserver implements GameSessionListener {

        @Override
        public void updateCredits(int credits) {
            log.trace("This is called via listener framework");
            getState(ResourceStateClient.class).updateCredits(credits);
        }
    }

    /**
     * Hooks into the CommandConsoleState to forward messages to the chat
     * service.
     */
    private class ChatCommandEntry implements CommandEntry {

        @Override
        public void runCommand(String cmd) {
            getState(ConnectionState.class).getService(ChatClientService.class).sendMessage(cmd);
        }
    }

    /**
     * Notified by the server about chat-releated events.
     */
    private class ChatSessionObserver implements ChatSessionListener {

        @Override
        public void playerJoined(int clientId, String playerName) {
            getState(MessageState.class).addMessage("> " + playerName + " has joined.", ColorRGBA.Yellow);
        }

        @Override
        public void newMessage(int clientId, String playerName, String message) {
            message = message.trim();
            if (message.length() == 0) {
                return;
            }
            getState(MessageState.class).addMessage(playerName + " said:" + message, ColorRGBA.White);
        }

        @Override
        public void playerLeft(int clientId, String playerName) {
            getState(MessageState.class).addMessage("> " + playerName + " has left.", ColorRGBA.Yellow);
        }
    }
}
