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
package infinity.systems;

import com.simsilica.es.EntityId;
import com.simsilica.mphys.AbstractShape;
import com.simsilica.mphys.Contact;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/**
 * An event publisher state. Other states will queue up events in this state, 
 * and this state will act as interface to 'bots' that subscribe to the events
 * @author Asser
 */
public class EventPublisherSystem extends AbstractGameSystem {
    /**
     * ArenaJoined
     * ArenaList
     * BallPosition
     * FileArrived
     * FlagClaimed
     * FlagDropped
     * FlagPosition
     * FlagReward
     * FlagVictory
     * FrequencyChange
     * FrequencyShipChange
     * InterProcessEvent
     * KotHReset
     * LoggedOn
     * MapInformation
     * Message
     * PasswordPacketResponse
     -- * PlayerBanned
     -- * PlayerDeath
     -- * PlayerEntered
     -- * PlayerLeft
     * PlayerPosition // omitted because its continuous data
     * Prize
     * ScoreReset
     * ScoreUpdate
     * SoccerGoal
     * SocketMessageEvent
     * SQLResultEvent
     * SyncRequest
     * SubspaceEvent
     * TurfFlagUpdate
     * TurretEvent
     * WatchDamage
     --* WeaponFired
     */
    
    @Override
    protected void initialize() {
        
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }

    @Override
    public void update(SimTime tpf) {

    }
}
