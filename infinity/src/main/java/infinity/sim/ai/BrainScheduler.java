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

import java.util.*;

import org.slf4j.*;

import com.simsilica.sim.*;

/**
 *  Keeps track of a bunch of brains and their current scheduling.
 *  Note: this is a single-threaded data structure and should not
 *  be shared across threads.
 *
 *  @author    Paul Speed
 */
public class BrainScheduler {
    static Logger log = LoggerFactory.getLogger(BrainScheduler.class);
 
    // Just keeps track of all of the brains currently being managed
    // for easier checking.
    private Set<Brain> brains = new HashSet<>();
    
    // Something simple for now... until we need things like
    // rescheduling and stuff.
    private LinkedList<Brain> schedule = new LinkedList<>();
    
    // Queued up brains that may need rescheduling
    private Set<Brain> reschedule = new HashSet<>();

    public BrainScheduler() {
    }
 
    /**
     *  Adds the brain to this scheduler for management.  The brain
     *  will be run at its next heartbeat and all subsequent heardbeats
     *  until removed.
     */
    public void add( Brain brain ) {
        if( !brains.add(brain) ) {
            throw new IllegalArgumentException("Brain is already being managed:" + brain);
        }
        brain.initialize(this);
        schedule(brain);
    }
           
    /**
     *  Forces the brain to get removed and readded to the schedule in case
     *  its next heartbeat time has changed.
     */
    public void reschedule( Brain brain ) {
        log.info("reschedule(" + brain + ")");
        reschedule.add(brain);
    }

    /**
     *  Removes the brain from the scheduler, no further processing
     *  will happen for the specified brain.
     */
    public boolean remove( Brain brain ) {
        if( brains.remove(brain) ) {
            schedule.remove(brain);
            brain.terminate(this);
            return true;
        }
        return false; 
    }
 
    /**
     *  Called by the brain management code to get the currently scheduled
     *  and ready brains to think.
     */
    public void update( SimTime time ) {
    
        // Reschedule any pending reskeds
        if( !reschedule.isEmpty() ) {
            // Not efficient but functional
            schedule.removeAll(reschedule);
            for( Brain b : reschedule ) {
                schedule(b);
            }
            reschedule.clear();
        }          
        think(time);
    }

    protected void schedule( Brain brain ) {
        log.info("schedule(" + brain + ")");
        
        if( schedule.isEmpty() ) {
            schedule.add(brain);
            return;
        }
        
        long search = brain.getNextHeartbeat();
        for( ListIterator<Brain> it = schedule.listIterator(0); it.hasNext(); ) {
            Brain item = it.next();
            // Just in case, abort if we find ourselves
            if( item == brain ) {
                return;
            }
            if( item.getNextHeartbeat() > search ) {
                // Need to be before this item so back up one
                it.previous();
                it.add(brain);
                return;
            }
        }
        // Made it all the way through the list without something that
        // should be run after us... so just add it to the end
        schedule.add(brain); 
    }
        
    protected void think( SimTime time ) { 
        if( schedule.isEmpty() ) {
            return;
        }                
        // Run through all of the current 'expired' heartbeats
        long t = time.getTime();
        Brain brain = null;
        while( (brain = schedule.getFirst()) != null ) {
            if( brain.getNextHeartbeat() > t ) {
                break;
            }
            // Else this should be run
            brain.think(time);
            schedule.removeFirst();
                
            // Sanity check the heartbeat
            if( brain.getNextHeartbeat() <= t ) {
                log.warn("possible endless loop caused by non-advancing time for:" + brain
                            + " next heartbeat:" + brain.getNextHeartbeat() + " t:" + t);
            }
            schedule(brain);
        }
    }
 
    
}
