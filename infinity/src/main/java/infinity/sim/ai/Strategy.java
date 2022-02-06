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

import java.util.*;

import org.slf4j.*;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 *  Matches to a goal and creates action plans to meet that goal.
 *
 *  @author    Paul Speed
 */
public class Strategy<T extends Goal> {
    static Logger log = LoggerFactory.getLogger(Strategy.class);
 
    private ActionFactory<T> plan; 
    private ActionFactory<T> onDone;
    private ActionFactory<T> onFailed;
    
    private Predicate<String> touchTypes;
    private TouchListener onTouch;
    private MovementListener onMoved;
    private BlockedListener onBlocked;
    
    public Strategy( ActionFactory<T> plan ) {
        if( plan == null ) {
            throw new IllegalArgumentException("Plan cannot be null");
        }
        this.plan = plan; 
    }
    
    public Strategy<T> onDone( ActionFactory<T> onDone ) {
        this.onDone = onDone;
        return this;
    }

    public Strategy<T> onFailed( ActionFactory<T> onFailed ) {
        this.onFailed = onFailed;
        return this;
    }
    
    public Strategy<T> onTouch( Collection<String> types, TouchListener listener ) {
        if( types == null ) {
            touchTypes = Predicates.alwaysTrue();
        } else {
            touchTypes = Predicates.in(new HashSet<>(types));
        }
        this.onTouch = listener;
        return this;
    }

    public Strategy<T> onMoved( MovementListener onMoved ) {
        this.onMoved = onMoved;
        return this;
    }

    public Strategy<T> onBlocked( BlockedListener onBlocked ) {
        this.onBlocked = onBlocked;
        return this;
    }
    
    public boolean isInterestingTouch( String type ) {
        return touchTypes == null ? false : touchTypes.apply(type);
    }
    
    public Action plan( Brain brain, T goal ) {
        return plan.createAction(brain, goal); 
    } 

    public Action done( Brain brain, T goal ) {
        return onDone == null ? null : onDone.createAction(brain, goal); 
    }
     
    public Action failed( Brain brain, T goal ) {
        return onFailed == null ? null : onFailed.createAction(brain, goal); 
    }
    
    public boolean touch( Brain brain, TouchEvent event ) {
        if( onTouch != null ) {
            return onTouch.touch(brain, event);
        }
        return false;
    }
     
    public boolean objectMoved( Brain brain, SeenObject object ) {
        if( onMoved != null ) {
            return onMoved.objectMoved(brain, object);
        }
        return false;
    }
    
    public boolean blocked( Brain brain, Object blocker ) {
        if( onBlocked != null ) {
            return onBlocked.blocked(brain, blocker);
        }
        return false;
    } 
}
