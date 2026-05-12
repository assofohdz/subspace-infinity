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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.service.rmi.RmiClientService;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

import infinity.es.input.MovementInput;
import infinity.events.MapAction;
import infinity.net.GameSession;
import infinity.net.GameSessionListener;

/** Client-side RMI proxy for the server's {@link GameSession}. */
public class GameSessionClientService extends AbstractClientService implements GameSession {

    static Logger log = LoggerFactory.getLogger(GameSessionClientService.class);

    private RmiClientService rmiService;
    private GameSession delegate;

    private final GameSessionCallback sessionCallback = new GameSessionCallback();
    private final List<GameSessionListener> listeners = new CopyOnWriteArrayList<>();

    public GameSessionClientService() {
    }

    @Override
    public EntityId getAvatar() {
        return getDelegate().getAvatar();
    }

    @Override
    public void setView(final Quatd rotation, final Vec3d location) {
        if (log.isTraceEnabled()) {
            log.trace("setView(" + rotation + ", " + location + ")");
        }
        getDelegate().setView(rotation, location);
    }

    @Override
    public void setMovementInput(MovementInput input) {
        getDelegate().setMovementInput(input);
    }

    private GameSession getDelegate() {
        // Lazy lookup decouples from the account-service login sequence — otherwise
        // this service would need to listen on AccountSession just to know when
        // the remote object becomes available.
        if (delegate == null) {
            delegate = rmiService.getRemoteObject(GameSession.class);
            log.debug("delegate:{}", delegate);
            if (delegate == null) {
                throw new IllegalStateException("No game session found");
            }
        }
        return delegate;
    }

    // Called on the networking thread; not safe for visualization mutations.
    public void addGameSessionListener(final GameSessionListener l) {
        listeners.add(l);
    }

    public void removeGameSessionListener(final GameSessionListener l) {
        listeners.remove(l);
    }

    @Override
    protected void onInitialize(final ClientServiceManager s) {
        log.info("onInitialize({})", s);
        rmiService = getService(RmiClientService.class);
        if (rmiService == null) {
            throw new IllegalStateException("GameSessionClientService requires RMI service");
        }
        // Register early so server-initiated callbacks never beat us to the punch.
        log.info("Sharing session callback.");
        rmiService.share(sessionCallback, GameSessionListener.class);
    }

    @Override
    public void start() {
        log.debug("start()");
        super.start();
    }

    @Override
    public void move(final MovementInput movementForces) {
        // log.debug("move(" + movementForces + ")");
        getDelegate().move(movementForces);
    }

    @Override
    public EntityId getPlayer() {
        return getDelegate().getPlayer();
    }

    @Override
    public Vec3d getPlayerLocation() {
        return getDelegate().getPlayerLocation();
    }

    @Override
    public void action(final byte actionInput) {
        getDelegate().action(actionInput);
    }

    @Override
    public void attack(final byte attackInput) {
        getDelegate().attack(attackInput);
    }

    @Override
    public void avatar(final byte avatarInput) {
        getDelegate().avatar(avatarInput);
    }

    @Override
    public void toggle(final byte toggleInput) {
        getDelegate().toggle(toggleInput);
    }

    @Override
    public void map(final MapAction mapInput, final Vec3d coords) {
        getDelegate().map(mapInput, coords);
    }

    // Shared with the server over RMI for notifications.
    private class GameSessionCallback implements GameSessionListener {

        @Override
        public void setAvatar(final EntityId avatar) {

            log.info("setAvatar({})", avatar);
            for (final GameSessionListener l : listeners) {
                l.setAvatar(avatar);
            }
        }
    }
}
