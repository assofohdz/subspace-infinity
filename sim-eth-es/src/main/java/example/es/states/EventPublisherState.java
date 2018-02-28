/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/**
 * An event publisher state. Other states will queue up events in this state, 
 * and this state will act as interface to 'bots' that subscribe to the events
 * @author Asser
 */
public class EventPublisherState extends AbstractGameSystem {
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
