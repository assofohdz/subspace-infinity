/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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

package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.es.Frequency;
import infinity.es.MobType;
import infinity.es.ProbeInfo;
import infinity.es.input.CharacterInput;
import infinity.es.ship.Player;

public class AIEntities {

  private AIEntities() {
    // no instances
  }

  public static EntityId createMobShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      byte ship) {

    EntityId mob = GameEntities.createShip(spawnLoc, ed, owner, phys, createdTime, ship);
    byte flags = 0x0;
    ed.setComponent(mob, new CharacterInput(new Vec3d(), new Quatd(), flags));
    ed.setComponent(mob, MobType.create("Mob", ed));
    ed.setComponent(mob, new Name("Mob-"+String.valueOf(mob.getId())));
    ed.setComponent(mob, new ProbeInfo(new Vec3d(0, 0.1, 0.4), 0.3));
    ed.setComponent(mob, new Frequency(1));

    // Right now we create the entity with the player component, but we don't want the mob to be a
    // player
    ed.removeComponent(mob, Player.class);

    return mob;
  }
}
