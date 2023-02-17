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

package infinity.ai;

import org.slf4j.*;

import com.simsilica.mathd.*;
import com.simsilica.sim.SimTime;


/**
 *
 *
 *  @author    Paul Speed
 */
public class WalkTo implements Action {
    static Logger log = LoggerFactory.getLogger(WalkTo.class);

    private Vec3d target = new Vec3d();
    private double speed;
    private double range = 0;
    private double maxTime;

    // How often do we confirm our direction and movement   
    private double checkTime;

    private double heartBeat = 1;
    private double lastTime;
    private Vec3d lastPosition = null; 

    public WalkTo( Vec3d target, double speed, double range, double checkTime ) {
        this.target.set(target);
        this.speed = speed;
        this.range = range;
        this.checkTime = checkTime;
    }
        
    public static double getFacing( Vec3d dir ) {
        // Figure out which direction we need to go
        // Note: because 'z' is our default look dir we treat
        // z as cosine and x as sine.
        // Also, for now... 0 is south.  I'm going to regret not fixing
        // that the longer I wait.
        double sin = dir.x;
        double cos = dir.z;
        double facing = Math.atan2(sin, cos);
        if( facing < 0 ) {
            facing += Math.PI * 2; 
        }
        return facing;
    }

    @Override   
    public ActionStatus run( SimTime time, Brain brain ) {
        Actor actor = brain.getActor();
        Vec3d pos = actor.getPosition();
 
        // See if we've reached our goal
        Vec3d v = pos.subtract(target);
        double d1 = v.length();
        if( Math.abs(v.y) < 0.5 ) { 
            v.y = 0; // as the crow flies
        }
        double d2 = v.length();
        double d = Math.min(d1, d2); 
log.info("d1:" + d1 + "  d2:" + d2 + "    delta:" + v);
        if( d <= range ) {
            actor.move(new Vec3d());
            return ActionStatus.DONE;
        }

        if( d <= 1 ) {
            // Quicker heartbeat
            heartBeat = 0.1;
        } else {
            // Check every so often to see if we've failed
            // and reorient our direction.
            heartBeat = Math.max(1, checkTime);
        }
        
        // Figure out which direction we need to go
        // Note: because 'z' is our default look dir we treat
        // z as cosine and x as sine.
        double sin = target.x - pos.x;
        double cos = target.z - pos.z;  
        double facing = Math.atan2(sin, cos);
log.info("pos:" + pos + "  target:" + target + "   facing:" + Math.toDegrees(facing));
        //facing = 0; //-Math.PI * 0.25;        
        if( facing < 0 ) {
            facing += Math.PI * 2; 
        }
        actor.turnTo(facing);
        actor.move(new Vec3d(0, 0, speed));
 
        double t = time.getTimeInSeconds(); 
        if( lastPosition != null ) {
            // Check to see if we've failed
            double moved = pos.distance(lastPosition);
            double delta = t - lastTime;
            double speed = moved/delta; 

            // If we're stuck then we've failed
            if( speed < 0.2 ) {
log.info("******************** Aborting WalkTo");
                // Probably we want to be able to supply the brain
                // some information on why we failed.            
                actor.move(new Vec3d());
                return ActionStatus.FAILED;
            }
                        
        } else {
            lastPosition = new Vec3d();
        }
        lastPosition.set(actor.getPosition());
        lastTime = t;
                    
        return ActionStatus.RUNNING;
    }
    
    @Override 
    public void abort( Brain brain ) {
        Actor actor = brain.getActor();
        actor.turnTo(actor.getFacing());
        actor.move(new Vec3d());
    } 
    
    @Override   
    public double getHeartbeat( SimTime time ) {
        return heartBeat;
    }
    
    @Override
    public Action getLastAction() {
        return this;
    }
}


