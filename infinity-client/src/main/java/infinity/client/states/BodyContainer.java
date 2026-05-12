/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
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

package infinity.client.states;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.ext.mphys.ShapeInfo;

/** Tracks dynamic-body entities ({@link BodyPosition} + {@link ShapeInfo}) and binds each to a {@link Body} view. */
class BodyContainer extends EntityContainer<Body> {
  private final ModelViewState owner;

  BodyContainer(final ModelViewState owner, final EntityData ed) {
    // Because at least in this demo, shape and model are the same thing
    super(ed, BodyPosition.class, ShapeInfo.class);
    this.owner = owner;
  }

  @Override
  public Body[] getArray() {
    return super.getArray();
  }

  @Override
  protected Body addObject(final Entity e) {
    if (ModelViewState.log.isInfoEnabled()) {
      ModelViewState.log.info("add body for:{}", e.getId());
    }
    final Body object = new Body(owner, e);
    updateObject(object, e);
    return object;
  }

  @Override
  protected void updateObject(final Body object, final Entity e) {
    object.setShape(e.get(ShapeInfo.class));
    object.setPosition(e.get(BodyPosition.class));
  }

  @Override
  protected void removeObject(final Body object, final Entity e) {
    if (ModelViewState.log.isInfoEnabled()) {
      ModelViewState.log.info("remove body for:{}", e.getId());
    }
    object.release();
  }
}
