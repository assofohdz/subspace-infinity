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

/**
 * Marks static models visible at a delay so that they act similar to bodies which have a strict
 * visibility time. This is to make up for the fact that SpawnPosition doesn't have a timestamp
 * and that rigid bodies will typically have both a SpawnPosition and a BodyPosition. These two
 * compete and cause the object to flicker on creation: SpawnPosition makes it visible
 * BodyPosition makes it invisible BodyPosition + delay makes it visible again. A timestamp on
 * SpawnPosition would fix this but might be overkill.
 */
class MarkVisible {
  final Model model;
  final long visibleTime;

  MarkVisible(final Model model, final long visibleTime) {
    this.model = model;
    this.visibleTime = visibleTime;
  }

  public void update() {
    ModelViewState.log.info(
        "MarkVisible.update() useCount:{}  dynamic:{}", model.useCount, model.dynamic);
    // If the model is still static in some way then
    // we'll mark for static visibility
    if (model.useCount == 1 && model.dynamic) {
      ModelViewState.log.info("only dynamic... should already be visible.");
      // Somehow it's only dynamic... no spawn position at all
      return;
    }
    if (model.useCount == 0) {
      // Model is no longer in use
      ModelViewState.log.info("not used anymore");
      return;
    }

    ModelViewState.log.info("Marking static object visible:{}", model.entityId);
    // Should be safe to add our static visibility marker
    model.markVisible();
  }
}
