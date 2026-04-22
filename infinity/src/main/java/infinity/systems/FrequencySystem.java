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

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.StaticBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Flag;
import infinity.es.Frequency;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameSounds;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A system that handles the frequency of the flags and players.
 *
 * @author AFahrenholz
 */
public class FrequencySystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  private final Pattern freuencyChange = Pattern.compile("=(\\d+)");
  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> phys;
  private EntitySet freqencies;
  private EntitySet flags;
  private SimTime time;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class, true);
    phys = getSystem(PhysicsSpace.class, true);

    freqencies = ed.getEntities(Frequency.class);
    flags = ed.getEntities(Flag.class);

    InfinityChatHostedService chat = getSystem(InfinityChatHostedService.class);
    // Register consuming methods for patterns
    chat.registerPatternTriConsumer(
        freuencyChange,
        "The command to load a new map is ~loadArena <mapName>, where <mapName> is the "
            + "name of the map you want to load",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::changeFrequency));

    // Register this as a contact listener with the ContactSystem
    getSystem(ContactSystem.class, true).addListener(this);
  }

  /**
   * Changes the frequency of the player's avatar.
   *
   * @param entityId The id of the player
   * @param m The matcher that contains the frequency as group 1
   */
  private String changeFrequency(EntityId entityId, EntityId avatarEntityId, Matcher m) {
    ed.setComponent(avatarEntityId, new Frequency(Integer.parseInt(m.group(1))));
    return "Frequency changed to " + m.group(1);
  }

  @Override
  protected void terminate() {
    //Remove this as a contact listener with the ContactSystem
    getSystem(ContactSystem.class, true).removeListener(this);

    freqencies.release();
    freqencies = null;

    flags.release();
    flags = null;
  }

  @Override
  public void newContact(Contact contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    // For now, all flags are static and cannot be picked up, but can change frequencies
    if (body2 instanceof StaticBody) {
      EntityId ship = body1.id;
      EntityId flag = body2.id;

      // Check if entity one is a ship and has a frequency and if entity two is flag with a
      // different frequency
      if (freqencies.containsId(ship) && flags.containsId(flag)) {
        int shipFreq = freqencies.getEntity(ship).get(Frequency.class).getFrequency();
        // Check if flag has a frequency
        if (freqencies.containsId(flag)) {
          int flagFreq = freqencies.getEntity(flag).get(Frequency.class).getFrequency();
          if (shipFreq != flagFreq) {
            // Set the flag to the frequency of the ship
            ed.setComponent(
                flag,
                new Frequency(freqencies.getEntity(ship).get(Frequency.class).getFrequency()));
            GameSounds.createFlagSound(ed, EntityId.NULL_ID, phys, time.getTime(), body2.position);
          }
        } else {
          // Set the flag to the frequency of the ship
          ed.setComponent(
              flag, new Frequency(freqencies.getEntity(ship).get(Frequency.class).getFrequency()));
          GameSounds.createFlagSound(ed, EntityId.NULL_ID, phys, time.getTime(), body2.position);
        }
        contact.disable();
      }
    }
  }

  @Override
  public void update(SimTime time) {
    freqencies.applyChanges();
    flags.applyChanges();

    this.time = time;
  }
}
