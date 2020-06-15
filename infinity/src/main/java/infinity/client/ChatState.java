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

package infinity.client;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.*;

import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.state.*;
import infinity.MainGameFunctions;
import infinity.client.chat.ChatClientService;
import infinity.net.chat.ChatSessionListener;


/**
 *  Manages the chat entry and allows always-on toggling, etc..
 *
 *  @author    Paul Speed
 */
public class ChatState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(ChatState.class);

    private ChatSessionObserver chatSessionObserver = new ChatSessionObserver();
    private ChatCommandEntry    chatEntry = new ChatCommandEntry();
    private CommandEntry        originalCommandEntry;

    public ChatState() {
    }
 
    @Override   
    protected void initialize( Application app ) {
        log.info("initialize()");
        
        // Add a self-message because we're too late to have caught the
        // player joined message for ourselves.  (Please we'd want it to look like this, anyway.)
        getState(MessageState.class).addMessage("> You have joined the game.", ColorRGBA.Yellow);

        // Setup the chat related services
        getState(ConnectionState.class).getService(ChatClientService.class).addChatSessionListener(chatSessionObserver);
        this.originalCommandEntry = getState(CommandConsoleState.class).getCommandEntry(); 
        getState(CommandConsoleState.class).setCommandEntry(chatEntry);

        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.activateGroup(MainGameFunctions.IN_GAME);
        inputMapper.addDelegate(MainGameFunctions.F_CHAT_CONSOLE, 
                                getState(CommandConsoleState.class), "toggleConsole");               
    }
    
    @Override   
    protected void cleanup( Application app ) {
        
        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.deactivateGroup(MainGameFunctions.IN_GAME);        
        inputMapper.removeDelegate(MainGameFunctions.F_CHAT_CONSOLE, 
                                   getState(CommandConsoleState.class), "toggleConsole");       
         
        getState(MessageState.class).addMessage("> You have left the game.", ColorRGBA.Yellow);
        getState(CommandConsoleState.class).setCommandEntry(originalCommandEntry);
    }

    @Override
    protected void onEnable() {
    }            

    @Override
    protected void onDisable() {
    }            
    
    /**
     *  Hooks into the CommandConsoleState to forward messages to the
     *  chat service.
     */
    private class ChatCommandEntry implements CommandEntry {
        @Override
        public void runCommand( String cmd ) {
            getState(ConnectionState.class).getService(ChatClientService.class).sendMessage(cmd);
        }
    } 
    
    /**
     *  Notified by the server about chat-releated events.
     */
    private class ChatSessionObserver implements ChatSessionListener {
    
        @Override
        public void playerJoined( int clientId, String playerName ) {
            getState(MessageState.class).addMessage("> " + playerName + " has joined.", ColorRGBA.Yellow);
        }
 
        @Override
        public void newMessage( int clientId, String playerName, String message ) {
            message = message.trim();
            if( message.length() == 0 ) {
                return;
            }
            getState(MessageState.class).addMessage(playerName + " said:" + message, ColorRGBA.White);  
        }
    
        @Override
        public void playerLeft( int clientId, String playerName ) {
            getState(MessageState.class).addMessage("> " + playerName + " has left.", ColorRGBA.Yellow);  
        }
    }
}
