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

import java.util.Objects;

import org.slf4j.*;

import com.simsilica.crig.*;

/**
 *  Pumps animation time information to a character rig.
 *
 *  @author    Paul Speed
 */
public class AnimPump {
    static Logger log = LoggerFactory.getLogger(AnimPump.class);

    private CharacterRig rig;
    private String layer;
    private String action;
    private double time;
    private double speed;
    private double length;

    public AnimPump( CharacterRig rig, String layer ) {
        this.rig = rig;
        this.layer = layer;
    }

    public void setCurrentAction( String action, double speed ) {
//if( this.speed != speed ) {
//    log.info("action:" + action + "  speed:" + speed);
//}
        this.speed = speed;
        if( Objects.equals(this.action, action) ) {
            return;
        }
        this.time = 0;
        this.action = action;
        if( action != null ) {
log.info("setLayerAction(" + layer + ", " + action + ") speed:" + speed);
            rig.setLayerAction(layer, action);
            length = rig.getLayerDuration(layer);
        }
    }

    public String getCurrentAction() {
        return action;
    }

    public double getTime() {
        return time;
    }

    public void setSpeed( double speed ) {
//if( this.speed != speed ) {
//    log.info("setSpeed(" + speed + ")");
//}
        this.speed = speed;
    }

    public void update( double tpf ) {
//log.info("update(" + tpf + ") action:" + action);
        if( action == null ) {
            return;
        }
double lastTime = time;
        time += tpf * speed;
        time = time % length;
        if( time < 0 ) {
            time = (time + length) % length;
        }
if( lastTime > time ) {
    log.info("wrap at:" + time);
}
//log.info("time:" + time);
        rig.setTime(layer, time);
    }

}
