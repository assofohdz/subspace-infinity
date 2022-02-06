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

import com.simsilica.mathd.*;

import com.simsilica.es.*;
import com.simsilica.mblock.phys.*;


/**
 *  For lack of a better name. When a mob is looking around the world,
 *  this is how it sees objects. 
 *
 *  @author    Paul Speed
 */
public class SeenObject {
    // For now a concrete class instead of an interface and directly
    // tied to ES and MBlock.
    
    private EntityId entityId;
    private Vec3d position;
    private Quatd orientation;
    private Vec3d velocity;
    private MBlockShape shape;
    private String type;
    private double distance;

    public SeenObject( EntityId entityId, Vec3d position, Quatd orientation, Vec3d velocity, MBlockShape shape, String type, double distance ) {
        this.entityId = entityId;
        this.position = position.clone();
        this.orientation = orientation.clone();
        this.velocity = velocity.clone();
        this.shape = shape;
        this.type = type;
        this.distance = distance;
    } 
    
    public EntityId getId() {
        return entityId;        
    }
    
    public Vec3d getPosition() {
        return position;
    }
    
    public Quatd getOrientation() {
        return orientation;
    }
    
    public Vec3d getVelocity() {
        return velocity;
    }
    
    public MBlockShape getShape() {
        return shape;
    }
    
    public String getType() {
        return type;
    }
 
    public double getDistance() {
        return distance;
    }
 
    @Override
    public String toString() {
        return "SeenObject[" + entityId + ", " + type + "]";
    }   
}
