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
package infinity.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.HostedConnection;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.es.server.EntityDataHostedService;
import com.simsilica.event.EventBus;

import infinity.net.AccountEvent;
import infinity.net.AccountSession;
import infinity.net.AccountSessionListener;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;

/**
 * Provides super-basic account services like logging in. This could be expanded
 * to be more complicated based on a real game's needs. The basics have been
 * included here as a minimal example that includes the basic types of
 * communication necessary.
 *
 * @author Paul Speed
 */
public class AccountHostedService extends AbstractHostedConnectionService implements AccountManager {

    static Logger log = LoggerFactory.getLogger(AccountHostedService.class);
    /**
     * This Hashmap contains all the operators Key: Name of the operator [lowercase]
     * Value: level id (0-9)
     */
    private static HashMap<EntityId, AccessLevel> operators;

    private static final String ATTRIBUTE_SESSION = "account.session";
    private static final String ATTRIBUTE_PLAYER_NAME = "account.playerName";
    private static final String ATTRIBUTE_PLAYER_ENTITY = "account.playerEntity";

    private RmiHostedService rmiService;

    private String serverInfo;
    private EntityData ed;

    private HashMap<EntityId, HostedConnection> playerConnectionMap = new HashMap<>();

    public AccountHostedService(String serverInfo) {
        this.serverInfo = serverInfo;
        this.operators = new HashMap<>();
    }

    public static String getPlayerName(HostedConnection conn) {
        return conn.getAttribute(ATTRIBUTE_PLAYER_NAME);
    }

    public static EntityId getPlayerEntity(HostedConnection conn) {
        return conn.getAttribute(ATTRIBUTE_PLAYER_ENTITY);
    }

    @Override
    protected void onInitialize(HostedServiceManager s) {

        // Grab the RMI service so we can easily use it later
        this.rmiService = getService(RmiHostedService.class);
        if (rmiService == null) {
            throw new RuntimeException("AccountHostedService requires an RMI service.");
        }
    }

    @Override
    public void start() {
        EntityDataHostedService eds = getService(EntityDataHostedService.class);
        if (eds == null) {
            throw new RuntimeException("AccountHostedService requires an EntityDataHostedService");
        }
        this.ed = eds.getEntityData();
    }

    @Override
    public void startHostingOnConnection(HostedConnection conn) {

        // Add default access
        operators.put(conn.getAttribute(AccountHostedService.ATTRIBUTE_PLAYER_ENTITY), AccessLevel.PLAYER_LEVEL);

        log.debug("startHostingOnConnection(" + conn + ")");

        AccountSessionImpl session = new AccountSessionImpl(conn);
        conn.setAttribute(ATTRIBUTE_SESSION, session);

        // Expose the session as an RMI resource to the client
        RmiRegistry rmi = rmiService.getRmiRegistry(conn);
        rmi.share(session, AccountSession.class);
    }

    @Override
    public void stopHostingOnConnection(HostedConnection conn) {
        log.debug("stopHostingOnConnection(" + conn + ")");
        AccountSessionImpl account = conn.getAttribute(ATTRIBUTE_SESSION);
        if (account != null) {
            String playerName = getPlayerName(conn);
            log.debug("publishing playerLoggedOff event for:" + conn);
            // Was really logged on before
            EventBus.publish(AccountEvent.playerLoggedOff, new AccountEvent(conn, playerName, account.player));

            // clear the account session info
            account.dispose();
            conn.setAttribute(ATTRIBUTE_SESSION, account);
        }
    }

    /**
     * Manually adds an operator to the access list. For special use only. (Not
     * needed in any normal procedure.)
     *
     * @param id          the entity to add as operator
     * @param accessLevel Access level at which to add the name
     */
    public void addOperator(EntityId id, AccessLevel accessLevel) {
        if (accessLevel.level < AccessLevel.PLAYER_LEVEL.level || accessLevel.level > AccessLevel.OWNER_LEVEL.level) {
            return;
        }

        operators.put(id, accessLevel);
    }

    /**
     * Gets the hosted connection object of the player entity
     *
     * @param eId the player EntityId
     * @return the HostedConnection object for that player if it exists. If no
     *         connection, returns null
     */
    public HostedConnection getHostedConnection(EntityId eId) {
        if (playerConnectionMap.containsKey(eId)) {
            return playerConnectionMap.get(eId);
        }
        return null;
    }

    /**
     * The connection-specific 'host' for the AccountSession.
     */
    private class AccountSessionImpl implements AccountSession {

        private HostedConnection conn;
        private AccountSessionListener callback;
        private EntityId player;

        public AccountSessionImpl(HostedConnection conn) {
            this.conn = conn;

            // Note: at this point we won't be able to look up the callback
            // because we haven't received the client's RMI shared objects yet.
        }

        protected AccountSessionListener getCallback() {
            if (callback == null) {
                RmiRegistry rmi = rmiService.getRmiRegistry(conn);
                callback = rmi.getRemoteObject(AccountSessionListener.class);
                if (callback == null) {
                    throw new RuntimeException("Unable to locate client callback for AccountSessionListener");
                }
            }
            return callback;
        }

        @Override
        public String getServerInfo() {
            return serverInfo;
        }

        @Override
        public void login(String playerName) {
            log.info("login(" + playerName + ")");
            conn.setAttribute(ATTRIBUTE_PLAYER_NAME, playerName);

            // Create the player entity
            player = ed.createEntity();
            conn.setAttribute(ATTRIBUTE_PLAYER_ENTITY, player);
            ed.setComponents(player, new Name(playerName));
            log.info("Created player entity:" + player + " for:" + playerName);

            playerConnectionMap.put(player, conn);

            // And let them know they were successful
            getCallback().notifyLoginStatus(true);

            log.debug("publishing playerLoggedOn event for:" + conn);
            // Notify 'logged in' only after we've told the player themselves
            EventBus.publish(AccountEvent.playerLoggedOn, new AccountEvent(conn, playerName, player));
        }

        public void dispose() {
            // The player is the ship is the entity... so we need to delete
            // the ship
            playerConnectionMap.remove(player);
            ed.removeEntity(player);
        }
    }

    /**
     * Clears the access list.
     */
    public void clear() {

        // Custom clean method of operators
        // Leave the bot operator list entries intact
        Set<Entry<EntityId, AccessLevel>> operatorNames = operators.entrySet();

        synchronized (operators) {
            Iterator<Entry<EntityId, AccessLevel>> it = operatorNames.iterator(); // Must be in synchronized block

            while (it.hasNext()) {
                Entry<EntityId, AccessLevel> operator = it.next();

                if (operator.getValue() != AccessLevel.BOT_LEVEL) {
                    it.remove();
                }
            }
        }

        // autoAssign.clear();
    }

    /**
     * @return The entire access mapping of player ids to access levels
     */
    public Map<EntityId, AccessLevel> getList() {
        return operators;
    }

    /**
     * Given a id, return the access level associated. If none is found, return 0
     * (normal player).
     *
     * @param entityId Entity in question
     * @return Access level of the id provided
     */
    public AccessLevel getAccessLevel(EntityId entityId) {
        if (entityId == null) {
            return AccessLevel.PLAYER_LEVEL;
        }

        AccessLevel accessLevel = operators.get(entityId);

        if (accessLevel == null) {
            return AccessLevel.PLAYER_LEVEL;
        } else {
            return accessLevel;
        }
    }

    public boolean isAtLeastAtAccessLevel(EntityId id, AccessLevel accessLevel) {
        return getAccessLevel(id).level >= accessLevel.level;
    }

    /**
     * Checks if a given id has at least bot operator level status. Bots
     * automatically make themselves the bot operator level after logging in.
     *
     * @param id Name in question
     * @return True if player has at least the bot operator level status
     */
    public boolean isBot(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.BOT_LEVEL.level;
    }

    /**
     * Checks if a given id has bot operator level status. Bots automatically make
     * themselves the bot operator level after logging in.
     *
     * @param id Name in question
     * @return True if player has the bot operator level status
     */
    public boolean isBotExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.BOT_LEVEL; // || getAccessLevel(id) == SYSOP_LEVEL) &&
                                                            // !sysops.contains(id)) {
    }

    /**
     * Check if a given id is at least of Outsider status. NOTE: Outsider is a
     * special status provided to coders who are not members of staff. They are able
     * to use some bot powers that ZHs can't, but can't generally use event bots.
     *
     * @param id Name in question
     * @return True if player is at least an Outsider
     */
    public boolean isOutsider(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.OUTSIDER_LEVEL.level;
    }

    /**
     * Check if a given id is an Outsider.
     *
     * @param id Name in question
     * @return True if player is a Outsider
     */
    public boolean isOutsiderExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.OUTSIDER_LEVEL;
    }

    /**
     * Check if a given id is at least of ER status.
     *
     * @param id Name in question
     * @return True if player is at least an ER
     */
    public boolean isLR(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.LR_LEVEL.level;
    }

    /**
     * Check if a given id is an ER.
     *
     * @param id Name in question
     * @return True if player is an ER
     */
    public boolean isLRExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.LR_LEVEL;
    }

    /**
     * Check if a given id is at least of ZH status.
     *
     * @param id Name in question
     * @return True if player is at least a ZH
     */
    public boolean isZH(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.ZH_LEVEL.level;
    }

    /**
     * Check if a given id is a ZH.
     *
     * @param id Name in question
     * @return True if player is a ZH
     */
    public boolean isZHExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.ZH_LEVEL;
    }

    /**
     * Check if a given id is at least of ER status.
     *
     * @param id Name in question
     * @return True if player is at least an ER
     */
    public boolean isER(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.ER_LEVEL.level;
    }

    /**
     * Check if a given id is an ER.
     *
     * @param id Name in question
     * @return True if player is an ER
     */
    public boolean isERExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.ER_LEVEL;
    }

    /**
     * Check if a given id is at least of Mod status.
     *
     * @param id Name in question
     * @return True if player is at least a Mod
     */
    public boolean isModerator(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.MODERATOR_LEVEL.level;
    }

    /**
     * Check if a given id is a Mod.
     *
     * @param id Name in question
     * @return True if player is a Mod
     */
    public boolean isModeratorExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.MODERATOR_LEVEL;
    }

    /**
     * Check if a given id is at least of HighMod status. NOTE: HighMod is a special
     * status given to experienced mods, allowing them access to certain features
     * that are normally only allowed to SMod+. Usually they are league ops or hold
     * another important position requiring this status.
     *
     * @param id Name in question
     * @return True if player is at least a HighMod
     */
    public boolean isHighmod(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.HIGHMOD_LEVEL.level;
    }

    /**
     * Check if a given id is a HighMod.
     *
     * @param id Name in question
     * @return True if player is a HighMod
     */
    public boolean isHighmodExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.HIGHMOD_LEVEL;
    }

    /**
     * Check if a given id is at least of Developer status.
     *
     * @param id Name in question
     * @return True if player is at least an ER
     */
    public boolean isDeveloper(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.DEV_LEVEL.level;
    }

    /**
     * Check if a given id is a Developer.
     *
     * @param id Name in question
     * @return True if player is a Developer
     */
    public boolean isDeveloperExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.DEV_LEVEL;
    }

    /**
     * Check if a given id is at least of SMod status.
     *
     * @param id Name in question
     * @return True if player is at least a SMod
     */
    public boolean isSmod(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.SMOD_LEVEL.level;
    }

    /**
     * Check if a given id is a SMod.
     *
     * @param id Name in question
     * @return True if player is a SMod.
     */
    public boolean isSmodExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.SMOD_LEVEL;
    }

    /**
     * Check if a given id is at least of Sysop status.
     *
     * @param id Name in question
     * @return True if player is at least a Sysop
     */
    public boolean isSysop(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.SYSOP_LEVEL.level;
    }

    public boolean isSysopExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.SYSOP_LEVEL; // || sysops.contains(id)) {
    }

    /**
     * Check if a given id is an owner.
     *
     * @param id Name in question
     * @return True if player is an owner
     */
    public boolean isOwner(EntityId id) {

        return getAccessLevel(id).level >= AccessLevel.OWNER_LEVEL.level;
    }

    /**
     * (REDUNDANT) Check if a given id is an owner.
     *
     * @param id Name in question
     * @return True if player is an owner
     * @deprecated Exactly the same functionality as isOwner, as no higher access
     *             level exists.
     */
    @Deprecated
    public boolean isOwnerExact(EntityId id) {

        return getAccessLevel(id) == AccessLevel.OWNER_LEVEL;
    }

    /**
     * Given an access level, returns all players who match that access level.
     *
     * @param accessLevel A number corresponding to the OperatorList access standard
     * @return HashSet of all players of that access level.
     */
    public HashSet<EntityId> getAllOfAccessLevel(AccessLevel accessLevel) {
        if (accessLevel.level < AccessLevel.PLAYER_LEVEL.level || accessLevel.level > AccessLevel.OWNER_LEVEL.level) {
            return null;
        }

        HashSet<EntityId> gathered = new HashSet<>();

        for (Entry<EntityId, AccessLevel> operator : operators.entrySet()) {
            if (operator.getValue() == accessLevel) {
                gathered.add(operator.getKey());
            }
        }

        return gathered;
    }

}
