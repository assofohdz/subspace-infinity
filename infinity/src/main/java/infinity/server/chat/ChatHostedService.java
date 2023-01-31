/*
 * $Id$
 *
 * Copyright (c) 2016, Simsilica, LLC
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
package infinity.server.chat;

import infinity.sim.CommandMonoConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.HostedConnection;
import com.jme3.network.MessageConnection;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;

import com.simsilica.es.EntityId;

import infinity.net.chat.ChatSession;
import infinity.net.chat.ChatSessionListener;
import infinity.server.AccountHostedService;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandBiConsumer;
import infinity.sim.MessageTypes;

/**
 * HostedService providing a chat server for connected players. Some time during player connection
 * setup, the game must start hosting and provide the player name in order for the client to
 * participate.
 *
 * @author Paul Speed
 */
public class ChatHostedService extends AbstractHostedConnectionService implements ChatHostedPoster {

  private static final String ATTRIBUTE_SESSION = "chat.session";
  static Logger log = LoggerFactory.getLogger(ChatHostedService.class);
  private final int channel;
  private final List<ChatSessionImpl> players = new CopyOnWriteArrayList<>();
  private final HashMap<Pattern, CommandBiConsumer> patternBiConsumers;
  private final HashMap<Pattern, CommandMonoConsumer> patternMonoConsumer;
  private RmiHostedService rmiService;

  /**
   * Creates a new chat service that will use the default reliable channel for reliable
   * communication.
   */
  public ChatHostedService() {
    this(MessageConnection.CHANNEL_DEFAULT_RELIABLE);
  }

  /** Creates a new chat service that will use the specified channel for reliable communication. */
  public ChatHostedService(final int channel) {
    this.channel = channel;
    patternBiConsumers = new HashMap<>();
    patternMonoConsumer = new HashMap<>();
    // setAutoHost(false);
  }

  protected ChatSessionImpl getChatSession(final HostedConnection conn) {
    return conn.getAttribute(ATTRIBUTE_SESSION);
  }

  @Override
  protected void onInitialize(final HostedServiceManager s) {

    // Grab the RMI service so we can easily use it later
    rmiService = getService(RmiHostedService.class);
    if (rmiService == null) {
      throw new RuntimeException("ChatHostedService requires an RMI service.");
    }
  }

  /**
   * Starts hosting the chat services on the specified connection using a specified player name.
   * This causes the player to 'enter' the chat room and will then be able to send/receive messages.
   */
  public void startHostingOnConnection(final HostedConnection conn, final String playerName) {
    log.debug("startHostingOnConnection(" + conn + ")");

    final ChatSessionImpl session = new ChatSessionImpl(conn, playerName);
    conn.setAttribute(ATTRIBUTE_SESSION, session);

    // Expose the session as an RMI resource to the client
    final RmiRegistry rmi = rmiService.getRmiRegistry(conn);
    rmi.share((byte) channel, session, ChatSession.class);

    players.add(session);

    // Send the enter event to other players
    for (final ChatSessionImpl chatter : players) {
      if (chatter == session) {
        // Don't send our enter event to ourselves
        continue;
      }
      chatter.playerJoined(conn.getId(), playerName);
    }
  }

  /** Starts hosting the chat services on the specified connection using a generated player name. */
  @Override
  public void startHostingOnConnection(final HostedConnection conn) {
    startHostingOnConnection(conn, "Client:" + conn.getId());
  }

  @Override
  public void stopHostingOnConnection(final HostedConnection conn) {
    log.debug("stopHostingOnConnection(" + conn + ")");
    final ChatSessionImpl player = getChatSession(conn);
    if (player != null) {

      // Then we are still hosting on the connection... it's
      // possible that stopHostingOnConnection() is called more than
      // once for a particular connection since some other game code
      // may call it and it will also be called during connection shutdown.
      conn.setAttribute(ATTRIBUTE_SESSION, null);

      // Remove player session from the active sessions list
      players.remove(player);

      // Send the leave event to other players
      for (final ChatSessionImpl chatter : players) {
        if (chatter == player) {
          // Don't send our enter event to ourselves
          continue;
        }
        chatter.playerLeft(player.conn.getId(), player.name);
      }
    }
  }

  protected void postMessage(final ChatSessionImpl from, final String message) {
    boolean matched = false;

    //Go through pattersn with 1 argument
    for (final Pattern pattern : patternBiConsumers.keySet()) {
      final Matcher matcher = pattern.matcher(message);
      if (matcher.matches()) {
        matched = true;
        final EntityId fromEntity = AccountHostedService.getPlayerEntity(from.getConn());
        final CommandBiConsumer cc = patternBiConsumers.get(pattern);
        // TODO: Implement account service to manage security levels
        // if (getService(AccountHostedService.class).isAtLeastAtAccessLevel(fromEntity,
        // cc.getAccessLevelRequired())) {
        cc.getConsumer().accept(fromEntity, matcher.group(1));
        // }
      }
    }
    // Go through patterns with no arguments
    for (final Pattern pattern : patternMonoConsumer.keySet()) {
      final Matcher matcher = pattern.matcher(message);
      if (matcher.matches()) {
        matched = true;
        final EntityId fromEntity = AccountHostedService.getPlayerEntity(from.getConn());
        final CommandMonoConsumer cc = patternMonoConsumer.get(pattern);

        cc.getConsumer().accept(fromEntity);
      }
    }

    if (matched) {
      return;
    }

    log.info("chat> " + from.name + " said:" + message);
    for (final ChatSessionImpl chatter : players) {
      chatter.newMessage(from.conn.getId(), from.name, message);
    }
  }
  // This method doesn't match patterns. It is only called from other modules, not from player
  // clients. Could potentially allow matching to allow modules to chain commands to other modules

  @Override
  public void postPublicMessage(final String from, final int messageType, final String message) {
    log.info("chat> " + from + " said:" + message);
    for (final ChatSessionImpl chatter : players) {
      chatter.newMessage(0, from, message);
    }
  }

  /**
   * @param pattern the pattern to match
   * @param description the help description of the pattern
   * @param c a consumer taking the message and the sender entity id
   */
  @Override
  public void registerPatternBiConsumer(
      final Pattern pattern, final String description, final CommandBiConsumer c) {
    // TODO: For now, only one consumer per pattern (we could potentially have
    // multiple)
    patternBiConsumers.put(pattern, c);

    // TODO: Post message only to those who have the proper access level
    postPublicMessage("System", MessageTypes.MESSAGE, description);
  }

  @Override
  public void registerPatternMonoConsumer(Pattern pattern, String description, CommandMonoConsumer c) {
    patternMonoConsumer.put(pattern, c);

    postPublicMessage("System", MessageTypes.MESSAGE, description);
  }

  /**
   * Removes a pattern to be consumed
   *
   * @param pattern the pattern to remove comsumption of
   */
  @Override
  public void removePatternConsumer(final Pattern pattern) {
    patternBiConsumers.remove(pattern);
  }

  @Override
  public void postPrivateMessage(
      final String from,
      final int messageType,
      final EntityId targetEntityId,
      final String message) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose
    // Tools | Templates.
  }

  @Override
  public void postTeamMessage(
      final String from, final int messageType, final int targetFrequency, final String message) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose
    // Tools | Templates.
  }

  @Override
  public void registerCommandConsumer(
      final String cmd, final String helptext, final CommandBiConsumer c) {
    // TODO: Put together the pattern that will match, depending on the sender and
    // the command

    // TODO: Post message only to those who have the proper access level
    postPublicMessage("System", MessageTypes.MESSAGE, helptext);
  }

  @Override
  public void removeCommandConsumer(final String cmd) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose
    // Tools | Templates.

  }

  /**
   * The connection-specific 'host' for the ChatSession. For convenience this also implements the
   * ChatSessionListener. Since the methods don't collide at all it's convenient for our other code
   * not to have to worry about the internal delegate.
   */
  private class ChatSessionImpl implements ChatSession, ChatSessionListener {

    private final HostedConnection conn;
    private final String name;
    private ChatSessionListener callback;

    public ChatSessionImpl(final HostedConnection conn, final String name) {
      this.conn = conn;
      this.name = name;

      // Note: at this point we won't be able to look up the callback
      // because we haven't received the client's RMI shared objects yet.
    }

    protected HostedConnection getConn() {
      return conn;
    }

    protected ChatSessionListener getCallback() {
      if (callback == null) {
        final RmiRegistry rmi = rmiService.getRmiRegistry(conn);
        callback = rmi.getRemoteObject(ChatSessionListener.class);
        if (callback == null) {
          throw new RuntimeException("Unable to locate client callback for ChatSessionListener");
        }
      }
      return callback;
    }

    @Override
    public void sendMessage(final String message) {
      postMessage(this, message);
    }

    @Override
    public List<String> getPlayerNames() {
      final List<String> results = new ArrayList<>();
      for (final ChatSessionImpl chatter : players) {
        results.add(chatter.name);
      }
      return results;
    }

    @Override
    public void playerJoined(final int clientId, final String playerName) {
      getCallback().playerJoined(clientId, playerName);
    }

    @Override
    public void newMessage(final int clientId, final String playerName, final String message) {
      getCallback().newMessage(clientId, playerName, message);
    }

    @Override
    public void playerLeft(final int clientId, final String playerName) {
      getCallback().playerLeft(clientId, playerName);
    }
  }
}
