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

import com.google.common.base.MoreObjects;

import com.simsilica.es.*;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.*;
import com.simsilica.sim.SimTime;

/**
 *  For lack of a better name, the Brain is what combines the goals,
 *  strategies, and action queue of a mob.
 *
 *  @author    Paul Speed
 */
public class Brain {
    static Logger log = LoggerFactory.getLogger(Brain.class);
 
    private BrainScheduler scheduler;
    private EntityData ed;   
    private EntityId id;
    private Actor actor;
 
    private BrainConfiguration config;
 
    private long nextHeartbeat;

    private Goal currentGoal;
    private Strategy currentStrategy;
 
    private LinkedList<Goal> failedGoals = new LinkedList<>();
 
    private Set<TouchEvent> pendingTouches = new HashSet<>();
 
    // For local runtime storage that can override configuration
    // properties.  Note: as implemented, this would be for transient
    // information that would not persist.  Probably we want to let
    // the app specify its own runtime properties implementation.
    private Map<String, Object> localProperties = new HashMap<>();
    
    private Action action;
 
    private Goal lastGoal;

    // Used to force the status in the next think() pass and skip
    // the regular action processing... for when failing goals, etc.
    private ActionStatus forcedStatus;
    
    public Brain( EntityData ed, EntityId id, BrainConfiguration config ) {
        this.ed = ed;
        this.id = id;
 
        //config = BrainConfigurations.createChicken(ed);
        this.config = config;
    }
 
    public void initialize( BrainScheduler scheduler ) {
        this.scheduler = scheduler;
    }
    
    public void terminate( BrainScheduler scheduler ) {
        this.scheduler = null;
    }
    
    public EntityId getId() {
        return id;
    }
    
    public void setActor( Actor actor ) {
        log.info("setActor(" + actor + ")");
        this.actor = actor;
    }
    
    public Actor getActor() {
        return actor;
    }
 
    public long getNextHeartbeat() {
        return nextHeartbeat;
    }

    // Mostly for debugging/info right now.
    public Collection<Goal> getFailedGoals() {
        return failedGoals;
    }

    public void setProperty( String name, Object value ) {
        if( value == null ) {
            // Go back to the default
            localProperties.remove(name);
        } else {
            localProperties.put(name, value);
        }   
    }
    
    public <T> T getProperty( String name, T defaultValue ) {
        T result = (T)localProperties.get(name);
        if( result != null ) {
            return result;
        }
        return config.getProperty(name, defaultValue);
    }

    public Goal getCurrentGoal() {
        return currentGoal;
    }
 
    public boolean objectMoved( SeenObject obj ) {
        if( currentStrategy != null ) {
            if( currentStrategy.objectMoved(this, obj) ) {
                return true;
            }
        }
        // Else try the default
        if( config.getDefaultStrategy() != null ) {
            return config.getDefaultStrategy().objectMoved(this, obj);
        }
        return false;
    }
 
    // Can't provide any other information right now because contact
    // doesn't really have it... We'll pretend we do for now with
    // an Object.
    public boolean blocked( Object blocker ) {
        if( currentStrategy != null ) {
            if( currentStrategy.blocked(this, blocker) ) {
                return true;
            }
        }
        // Else try the default
        if( config.getDefaultStrategy() != null ) {
            return config.getDefaultStrategy().blocked(this, blocker);
        }
        return false;
    }
    
    public boolean isInterestingTouch( String type ) {
        if( currentStrategy != null ) {
            if( currentStrategy.isInterestingTouch(type) ) {
                return true;
            }
        }
        // Check the defaults
        if( config.getDefaultStrategy() != null ) {
            return config.getDefaultStrategy().isInterestingTouch(type);
        }
        
        return false;
    }
    
    public void touch( TouchEvent event ) {
        if( pendingTouches.add(event) ) {
            // Make sure we get a chance to evaluate the event by
            // rescheduling ourselves for 'now'
            nextHeartbeat = 0;
            scheduler.reschedule(this);   
        }
    }
 
    public boolean isFailedGoal( Goal goal ) {
        return failedGoals.contains(goal);
    }
    
    protected void addFailedGoal( Goal goal ) {
        failedGoals.add(goal);
        
        // Chickens can only remember 3 past failures
        while( failedGoals.size() > 3 ) {
            failedGoals.removeFirst();
        } 
    }
 
    protected Goal selectGoal() {
        return config.selectGoal(this);
    }
    
    protected Strategy selectStrategy( Goal goal ) {
log.info("selectStrategy(" + goal + ")");    
        //Strategy result = strategies.get(goal.getClass());
        Strategy result = config.getStrategy(goal.getClass());
log.info(" found:" + result);
        if( result == null ) {
            log.error("No strategy found to support goal:" + goal);
            // We can't use a default strategy because some strategies
            // only work with certain goal types.  Something to maybe
            // address in the future.  Or make sure that default strategies
            // are always generic.
        }        
        return result;   
    }
    
    protected Action makePlan( Strategy strategy, Goal goal ) {
log.info("makePlan(" + strategy + ", " + goal + ")");
        return strategy.plan(this, goal);
    }
 
    /**
     *  Overrides the existing goal with a new higher priority goal.
     */   
    public void newGoal( Goal goal ) {
log.info("newGoal(" + goal + ")");    
        // We should just be able to abort the current action
        // set the current goal and clear the current strategy+action.
        if( action != null ) {
            action.abort(this);
        }
        action = null;
        currentStrategy = null;
        currentGoal = goal;
        nextHeartbeat = 0; // schedule immediately
        
        // Let the scheduler know that our next heartbeat
        // has changed.
        scheduler.reschedule(this); 
    }
    
    public void goalFailed() {
log.info("goalFailed()");    
        // We want to force a FAILED status the next pass through 
        // think.
        forcedStatus = ActionStatus.Failed;
        nextHeartbeat = 0; // schedule immediately
        scheduler.reschedule(this); 
    }
    
    protected boolean deliverTouches( Set<TouchEvent> events ) {
        // Without being able to sort by any kind of priority, 
        // try to deliver to the current strategy first and stop
        // at the first handled one.
        if( currentStrategy != null ) {
            for( TouchEvent event : pendingTouches ) {
                if( currentStrategy.touch(this, event) ) {
                    // It was handled and changed the goal
                    return true;
                }
            }
        }
        // Try the defaults
        if( config.getDefaultStrategy() != null ) {
            for( TouchEvent event : pendingTouches ) {
                if( config.getDefaultStrategy().touch(this, event) ) {
                    // It was handled and changed the goal
                    return true;
                }
            }
        }
         
        return false;
    }
    
    public void think( SimTime time ) {
        log.info("think():" + actor);

        if( !pendingTouches.isEmpty() ) {
            deliverTouches(pendingTouches);
            pendingTouches.clear();
        }
 
        if( action == null ) {
            if( currentGoal == null ) {
                currentGoal = selectGoal();
actor.say(time.getTime(), time.getFutureTime(1.0), String.valueOf(currentGoal));
                currentStrategy = null;
            }
            if( currentStrategy == null ) {
                currentStrategy = selectStrategy(currentGoal);
                try {
                    action = makePlan(currentStrategy, currentGoal);
                } catch( RuntimeException e ) {
                    log.error("Error making plan for:" + currentGoal + ", strategy:" + currentStrategy, e);
                    nextHeartbeat = time.getFutureTime(0.001);
                    currentGoal = null;
                    return;        
                } 
            }
        }
                           
        ActionStatus status;
        if( forcedStatus != null ) {
            status = forcedStatus;
            forcedStatus = null;
        } else {
            status = action.run(time, this);
        }
        if( status == ActionStatus.Running ) {
            // See how long to wait
            double next = action.getHeartbeat(time);
            
            // Next should always be a little more than 0 but we
            // get stuck if the actions return a bad time.  So we'll
            // check, warn, and adjust
            if( next <= 0 ) {
                log.warn("Bad heartbeat value from:" + action + "  heartbeat:" + next);
                next = 0.001;
            }
            
            nextHeartbeat = time.getFutureTime(next);
            return;
        }
 
        if( currentStrategy == null ) {
            // This was a tail action from a finished strategy... so clear
            // it and get ready for the next goal
            action = null;
        } else {
            // See how we faired
            switch( status ) {
                case Done:
                    action = currentStrategy.done(this, currentGoal);
                    
                    // Clear our memory of failed goals
                    failedGoals.clear();
                    
                    break; 
                case Failed:
                    if( currentGoal != null ) {
                        currentGoal.setFailedAction(action.getLastAction());
                        addFailedGoal(currentGoal);
                    }
                    action = currentStrategy.failed(this, currentGoal);
                    break; 
            }
            // That particular strategy is done either way
            currentStrategy = null;
        }

        // If there is no follow-on action then we're ready
        // for a new goal
        if( action == null ) {
            lastGoal = currentGoal;
            currentGoal = null;
        }
                
        // Come back soon
        nextHeartbeat = time.getFutureTime(0.001);
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass().getSimpleName())
            .add("entityId", id)
            .toString();
    }
}


