/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.api.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.Torque;
import org.dyn4j.geometry.Vector2;

/**
 * Represents the linear and angular velocity for an entity.
 *
 * @author Asser Fahrenholz
 */
public class PhysicsForce implements EntityComponent {

    private Force force;
    private EntityId target;
    private Vector2 forceWorldCoords;

    public PhysicsForce() {
    }

    public PhysicsForce(EntityId target, Force force, Vector2 forceWorldCoords) {
        this.force = force;
        this.target = target;
        this.forceWorldCoords = forceWorldCoords;
    }

    public Vector2 getForceWorldCoords() {
        return forceWorldCoords;
    }

    public EntityId getTarget() {
        return target;
    }

    public Force getForce() {
        return force;
    }

    @Override
    public String toString() {
        return "PhysicsForce{" + "force=" + force + ", target=" + target + ", forceWorldCoords=" + forceWorldCoords + '}';
    }
}
