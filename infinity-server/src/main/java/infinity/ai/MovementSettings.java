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

import com.google.common.base.MoreObjects;

/**
 * Parameters for how a mob will move in the world. TBD.
 *
 * @author Paul Speed
 */
public class MovementSettings {
  private double groundImpulse = 25;
  private double airImpulse = 1; // by default things can barely move in the air
  private double movementSpeed = 1.0;
  private double turnSpeed = Math.PI * 2.0;

  public MovementSettings() {
      // nothing to do
  }

  public double getGroundImpulse() {
    return groundImpulse;
  }

  public void setGroundImpulse(final double groundImpulse) {
    this.groundImpulse = groundImpulse;
  }

  public double getAirImpulse() {
    return airImpulse;
  }

  public void setAirImpulse(final double airImpulse) {
    this.airImpulse = airImpulse;
  }

  public double getMovementSpeed() {
    return movementSpeed;
  }

  public void setMovementSpeed(final double movementSpeed) {
    this.movementSpeed = movementSpeed;
  }

  public double getTurnSpeed() {
    return turnSpeed;
  }

  public void setTurnSpeed(final double turnSpeed) {
    this.turnSpeed = turnSpeed;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(MovementSettings.class)
        .add("groundImpulse", groundImpulse)
        .add("airImpulse", airImpulse)
        .add("movementSpeed", movementSpeed)
        .add("turnSpeed", turnSpeed)
        .toString();
  }
}
