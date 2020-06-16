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
package infinity.sim;

import com.simsilica.sim.AbstractGameSystem;

/**
 *
 * @author Asser
 */
public abstract class BaseGameModule extends AbstractGameSystem {

    private final ChatHostedPoster chp;
    private final AccountManager am;
    private final AdaptiveLoader loader;
    private final ArenaManager arenas;
    private final TimeManager time;
    private final PhysicsManager physics;

    /**
     * Instantiates a base game module with settings and reference to chat and
     * account services
     *
     * @param chp reference to the hosted chat server
     * @param am reference to the account management service
     * @param loader reference to the adaptive loading service
     * @param arenas
     * @param time
     */
    public BaseGameModule(ChatHostedPoster chp, AccountManager am, AdaptiveLoader loader, ArenaManager arenas, TimeManager time, PhysicsManager physics) {
        this.chp = chp;
        this.am = am;
        this.loader = loader;
        this.arenas = arenas;
        this.time = time;
        this.physics = physics;
    }


    public ChatHostedPoster getChp() {
        return chp;
    }

    public AccountManager getAm() {
        return am;
    }
    
    public AdaptiveLoader getLoader(){
        return loader;
    }

    public ArenaManager getArenas() {
        return arenas;
    }
    
    public TimeManager getTimeManager(){
        return time;
    }
    
    public PhysicsManager getPhysicsManager(){
        return physics;
    }
    
}
