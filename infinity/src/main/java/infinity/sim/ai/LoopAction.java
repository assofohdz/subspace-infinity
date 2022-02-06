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

package com.simsilica.demo.sim.ai;

import org.slf4j.*;

import com.simsilica.sim.SimTime;

/**
 *
 *
 *  @author    Paul Speed
 */
public class LoopAction<T extends TimedGoal> implements Action {
    static Logger log = LoggerFactory.getLogger(LoopAction.class);
 
    private T goal; 
    private ActionFactory<T> iterationFactory;
    private Action action;
 
    private double heartBeat = 0.0001;   
 
    public LoopAction( T goal, ActionFactory<T> iterationFactory ) {
        this.goal = goal;
        this.iterationFactory = iterationFactory;
    }  
    
    @Override
    public ActionStatus run( SimTime time, Brain brain ) {

        goal.updateTime(time);
        if( goal.getTimeRemaining() <= 0 ) {
            // We're done
            return ActionStatus.Done;
        }        
        if( action == null ) {
            action = iterationFactory.createAction(brain, goal);
        }
        
        ActionStatus status = action.run(time, brain);
        switch( status ) {
            case Running:
                heartBeat = action.getHeartbeat(time);
                return ActionStatus.Running;
            case Done:
                // Grab a new one next frame
                action = null;
                heartBeat = 0.001;
                return ActionStatus.Running;
            default:            
                // Loop failure
                return status;
        }
    }

    @Override 
    public void abort( Brain brain ) {
        if( action != null ) {
            action.abort(brain);
        }
    } 
 
    @Override
    public double getHeartbeat( SimTime time ) {
        return heartBeat;
    }
    
    @Override
    public Action getLastAction() {
        return action;
    }
}
