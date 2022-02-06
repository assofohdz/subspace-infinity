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

import java.util.Collection;

import com.simsilica.mathd.*;

import com.simsilica.es.*;

/**
 *  Implemented by objects that can be controlled by a Brain and
 *  its actions.  Provides some feedback of state but mostly control
 *  over the movement and animations of a mob's physical presence.
 *
 *  @author    Paul Speed
 */
public interface Actor {

    public Vec3d getPosition();
    
    public double getFacing();

    // Simple for now... may make this more like a steerable object
    // in the future and/or just have different wrappers that 
    // provide steering from regular inputs like below.
 
    public void turnTo( double facing );
    
    public void move( Vec3d move );

    // The first ES-specific method on Actor. Hm.
    // It's tempting to move this out to a "world" interface of some kind but
    // that ignores the fact that an actor may have sight-line limitations, perception
    // limitations, etc... all of which would have to be passed as some kind of arguments
    // to a world but will likely already be local to the actor.  This may change
    // as we move responsibilities around.  For now I'm going to put it here because
    // it needs to be somewhere.  In order to refactor something you have to have something.
    // Actually, returning EntityIds directly is probably not right anyway.  We
    // already went to the trouble to lookup shapes, positions, etc..  Should
    // return them.
    /**
     *  Returns any objects that the actor can see.
     *  Note: currently search will look behind the actor, too. 
     */
    public Iterable<SeenObject> search();
    
    /**
     *  Returns any objects that the actor can see of the specified type.
     *  If not types are specified then all perceived objects are returned.
     *  Note: currently search will look behind the actor, too. 
     */
    public Iterable<SeenObject> search( String... types );

    /**
     *  Returns any objects that the actor can see of the specified type.
     *  If not types are specified then all perceived objects are returned.
     *  Note: currently search will look behind the actor, too. 
     */
    public Iterable<SeenObject> search( Collection<String> types );

    // Returns the perceived location of an entity from the actor's perspective.
    public SeenObject look( EntityId id );
    public SeenObject look2( EntityId id );

    // These methods will probably go away but for now they are
    // convenient
    public void say( long startTime, long endTime, String text );
}
