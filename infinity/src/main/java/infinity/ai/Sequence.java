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

import java.util.Objects;
import org.slf4j.*;

import com.simsilica.sim.SimTime;

/**
 *  Executes other actions in order until they all succeed
 *  or one fails.
 *
 *  @author    Paul Speed
 */
public class Sequence implements Action {
    static Logger log = LoggerFactory.getLogger(Sequence.class);
 
    private Action[] actions;
    private int current = 0;
    private double heartBeat;
 
    public Sequence( Action... actions ) {
        this.actions = actions;
    } 
    
    @Override
    public ActionStatus run( SimTime time, Brain brain ) {
        if( current >= actions.length ) {
            return ActionStatus.DONE;
        }
        ActionStatus status = actions[current].run(time, brain);
        if (Objects.requireNonNull(status) == ActionStatus.DONE) {
            current++;
            if (current >= actions.length) {
                return ActionStatus.DONE;
            }
            // come back right away to give the next action
            // a chance
            heartBeat = 0.01;
            return ActionStatus.RUNNING;
        } else if (status == ActionStatus.RUNNING) {
            heartBeat = actions[current].getHeartbeat(time);
            return ActionStatus.RUNNING;
        }
        // If we got here then some kind of error happened
        return ActionStatus.FAILED;
    }

    @Override 
    public void abort( Brain brain ) {
        if( current >= actions.length ) {
            return;
        }
        actions[current].abort(brain);
    } 
 
    @Override
    public double getHeartbeat( SimTime time ) {
        return heartBeat;
    }
 
    @Override   
    public Action getLastAction() {
        if( current >= actions.length ) {
            return null;
        }
        return actions[current];
    }
    
}
