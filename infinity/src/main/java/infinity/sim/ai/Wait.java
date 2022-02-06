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

import com.simsilica.sim.SimTime;

/**
 *  Action that simply pauses for some amount of time.  Can also be
 *  used as the basis for fire-once-and-wait style actions.
 *
 *  @author    Paul Speed
 */
public class Wait implements Action {
    static Logger log = LoggerFactory.getLogger(Wait.class);
    
    private double duration;
    private double endTime;
    
    public Wait( double duration ) {
        this.duration = duration;
    }
 
    public double getDuration() {
        return duration;
    }
 
    protected boolean onStart( SimTime time, Brain brain ) {
        return true;
    }

    protected boolean onDone( SimTime time, Brain brain ) {
        return true;
    }
    
    @Override   
    public ActionStatus run( SimTime time, Brain brain ) {
        double t = time.getTimeInSeconds(); 
        if( endTime == 0 ) {
            // The first time we're run
            endTime = t + duration;
            if( !onStart(time, brain) ) {
                return ActionStatus.Failed;
            }
        }
        
        if( t >= endTime ) {
            if( !onDone(time, brain) ) {
                return ActionStatus.Failed;
            }
            return ActionStatus.Done;
        }
        return ActionStatus.Running;
    }
             
    @Override 
    public void abort( Brain brain ) {
    }
    
    @Override   
    public double getHeartbeat( SimTime time ) {
        // Just check us again the next time... we're basically a 'pause'
        // after we say the words
        return endTime - time.getTimeInSeconds();
    }
    
    @Override
    public Action getLastAction() {
        return this;
    }
}


