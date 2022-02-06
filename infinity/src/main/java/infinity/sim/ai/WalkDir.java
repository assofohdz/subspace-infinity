/*
 * $Id$
 * 
 * Copyright (c) 2021, Simsilica, LLC
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

package infinity.sim.ai;

import org.slf4j.*;

import com.simsilica.mathd.*;
import com.simsilica.sim.SimTime;

/**
 *  Action for walking in a particular direction for a certain
 *  amount of time.  Sometimes a mob has no particular place to
 *  be and just wants to head in some direction for a while.s
 *
 *  @author    Paul Speed
 */
public class WalkDir implements Action {
    static Logger log = LoggerFactory.getLogger(WalkDir.class);
    
    private double duration;    
    private double endTime;
    private double lastTime;
 
    // How often do we confirm our direction and movement   
    private double checkTime;
    
    private double facing;
    private Vec3d move = new Vec3d(); 
    private Vec3d lastPosition = new Vec3d(); 

    public WalkDir( Vec3d facing, Vec3d move, double duration, double checkTime ) {
        this(WalkTo.getFacing(facing), move, duration, checkTime);
    }
    
    public WalkDir( double facing, Vec3d move, double duration, double checkTime ) {
    
log.info("WalkDir(" + facing + ", " + move + ", " + duration + ")");     
        this.facing = facing;
        this.move.set(move);
        this.duration = duration;
        this.checkTime = checkTime;
    }
 
    @Override   
    public ActionStatus run( SimTime time, Brain brain ) {
        Actor actor = brain.getActor();
        actor.turnTo(facing);
        actor.move(move);
 
        Vec3d pos = actor.getPosition();
               
        double t = time.getTimeInSeconds(); 
        if( endTime == 0 ) {
            // The first time we're run
            endTime = t + duration;
        } else {
            double moved = pos.distance(lastPosition);
            double delta = t - lastTime;
            double speed = moved/delta; 
log.info("moved:" + speed);
            // If we're stuck then we've failed
            if( speed < move.length() * 0.35 ) {
log.info("******************** Aborting moveDir");
                // Probably we want to be able to supply the brain
                // some information on why we failed.            
                actor.move(new Vec3d());
                return ActionStatus.Failed;
            }
        }        
         
        lastPosition.set(actor.getPosition());
        lastTime = t;
        
        if( time.getTimeInSeconds() > endTime ) {
            actor.move(new Vec3d());
            return ActionStatus.Done;
        }
        return ActionStatus.Running;
    }
 
    @Override 
    public void abort( Brain brain ) {
        Actor actor = brain.getActor();
        actor.turnTo(actor.getFacing());
        actor.move(new Vec3d());
    } 
 
    @Override   
    public double getHeartbeat( SimTime time ) {
        // At most 1 second to check our status
        double delta = endTime - time.getTimeInSeconds();
        //return Math.min(1, delta);
        if( checkTime > 0 ) {
            // If checkTime is set then make sure we check back
            // in at mostleast that amount of time.  
            return Math.min(checkTime, delta);
        } 
        return delta;  
    }
    
    @Override
    public Action getLastAction() {
        return this;
    }
}
