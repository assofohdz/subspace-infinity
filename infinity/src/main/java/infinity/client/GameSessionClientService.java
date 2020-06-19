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

import com.jme3.math.Vector3f;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.*;

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.service.rmi.RmiClientService;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.net.GameSession;
import infinity.net.GameSessionListener;

/**
 *
 *
 * @author Paul Speed
 */
public class GameSessionClientService extends AbstractClientService
        implements GameSession {
    
    static Logger log = LoggerFactory.getLogger(GameSessionClientService.class);
    
    private RmiClientService rmiService;
    private GameSession delegate;
    
    private GameSessionCallback sessionCallback = new GameSessionCallback();
    private List<GameSessionListener> listeners = new CopyOnWriteArrayList<>();
    
    public GameSessionClientService() {
    }
    
    @Override
    public EntityId getAvatar() {
        return getDelegate().getAvatar();
    }
    
    @Override
    public void setView(Quatd rotation, Vec3d location) {
        if (log.isTraceEnabled()) {
            log.trace("setView(" + rotation + ", " + location + ")");
        }
        getDelegate().setView(rotation, location);
    }
    
    private GameSession getDelegate() {
        // We look up the delegate lazily to make the service more
        // flexible.  Otherwise we'd have to listen to the account service
        // to know when we'd fully logged on and that creates an unnecessary
        // dependency for a relatively small thing.  Easier just to lazily
        // load it upon request and hope the client is already handling the
        // state properly.
        if (delegate == null) {
            // Look it up
            this.delegate = rmiService.getRemoteObject(GameSession.class);
            log.debug("delegate:" + delegate);
            if (delegate == null) {
                throw new RuntimeException("No game session found");
            }
        }
        return delegate;
    }

    /**
     * Adds a listener that will be notified about account-related events. Note
     * that these listeners are called on the networking thread and as such are
     * not suitable for modifying the visualization directly.
     */
    public void addGameSessionListener(GameSessionListener l) {
        listeners.add(l);
    }
    
    public void removeGameSessionListener(GameSessionListener l) {
        listeners.remove(l);
    }
    
    @Override
    protected void onInitialize(ClientServiceManager s) {
        log.info("onInitialize(" + s + ")");
        this.rmiService = getService(RmiClientService.class);
        if (rmiService == null) {
            throw new RuntimeException("GameSessionClientService requires RMI service");
        }

        // Register the session right away even though the 'state' of the connection
        // is that we are not actually in the game yet.  Because the server is managing
        // that state, it does no harm for us to register the callback early and this
        // way we avoid any case where the server might try to call it before we are 
        // fully ready. (ie: it's friendlier to async messaging)
        log.info("Sharing session callback.");
        rmiService.share(sessionCallback, GameSessionListener.class);
    }

    /**
     * Called during connection setup once the server-side services have been
     * initialized for this connection and any shared objects, etc. should be
     * available.
     */
    @Override
    public void start() {
        log.debug("start()");
        super.start();
    }
    
    @Override
    public void move(Vec3d movementForces) {
        //log.debug("move(" + movementForces + ")");
        getDelegate().move(movementForces);
    }
    
    @Override
    public void createTile(String tileSet, double x, double y) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void removeTile(double x, double y) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void attackGuns() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void attackBomb() {
        //log.debug("attackBomb()");
        getDelegate().attackBomb();
    }
    
    @Override
    public void attackGravityBomb() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void placeMine() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void attackBurst() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void attackThor() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void repel() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void warp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void chooseShip(byte ship) {
        getDelegate().chooseShip(ship);
    }

    /**
     * Shared with the server over RMI so that it can notify us about account
     * related stuff.
     */
    private class GameSessionCallback implements GameSessionListener {
        
        @Override
        public void setAvatar(EntityId avatar) {
            
            log.info("setAvatar(" + avatar + ")");
            for (GameSessionListener l : listeners) {
                l.setAvatar(avatar);
            }
        }
    }
}
