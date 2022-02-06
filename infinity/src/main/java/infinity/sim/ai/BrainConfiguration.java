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

/**
 *  Holds the configuration about how a brain decides what 
 *  to do next: goal selector, strategies, perception criteria, etc..
 *
 *  @author    Paul Speed
 */
public class BrainConfiguration implements GoalSelector {
    static Logger log = LoggerFactory.getLogger(BrainConfiguration.class);

    private BrainConfiguration parent;
    private GoalSelector goalSelector;
    private Map<String, Object> properties = new HashMap<>();
    private Map<Class<? extends Goal>, Strategy> strategies = new HashMap<>();
    private Strategy defaultStrategy;
    
    public BrainConfiguration() {
    }

    /**
     *  Create a brain configuration that will delegate to the
     *  specified parent for anything not defined locally.
     */
    public BrainConfiguration( BrainConfiguration parent ) {
        this.parent = parent;
    }
 
    public void setParent( BrainConfiguration parent ) {
        this.parent = parent;
    }
 
    public BrainConfiguration getParent() {
        return parent;
    }
    
    public void setProperty( String name, Object value ) {
        properties.put(name, value);
    }
    
    public <T> T getProperty( String name, T defaultValue ) {
        T result = (T)properties.get(name);
        if( result == null && parent != null ) {
            result = parent.getProperty(name, defaultValue);
        }
        return result == null ? defaultValue : result;
    }

    public void setGoalSelector( GoalSelector goalSelector ) {
        this.goalSelector = goalSelector;
    }
    
    public GoalSelector getGoalSelector() {
        return goalSelector;
    }
 
    public <G extends Goal> void setStrategy( Class<G> type, Strategy<? super G> strategy ) {
        strategies.put(type, strategy);
    } 
    
    public <G extends Goal> Strategy<G> getStrategy( Class<G> type ) {
        Strategy<G> result = strategies.get(type);
        if( result != null ) {
            return result;
        }
        if( parent != null ) {
            return parent.getStrategy(type);            
        }
        return null;
    }
    
    public void setDefaultStrategy( Strategy strategy ) {
        this.defaultStrategy = strategy;
    }
    
    public Strategy getDefaultStrategy() {
        return defaultStrategy;
    }
    
    // By letting the configuration pick the goal it means we
    // can inherit from the parent config but it also means we
    // could have a chop-chain of selectors someday if we wanted.
    @Override 
    public Goal selectGoal( Brain brain ) {
        Goal result = goalSelector.selectGoal(brain);
        if( result != null ) {
            return result;
        }
        if( parent != null ) {
            return parent.selectGoal(brain);
        }
        return null;
    }      
}


