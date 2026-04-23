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

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;

public class BloomPostState extends BaseAppState {

  private FilterPostProcessor fpp;
  private BloomFilter bloom;

  @Override
  protected void initialize(final Application app) {
    fpp = new FilterPostProcessor(app.getAssetManager());
    bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
    bloom.setBloomIntensity(1.5f);
    bloom.setExposurePower(3.0f);
    bloom.setExposureCutOff(0.6f);
    bloom.setBlurScale(1.5f);
    fpp.addFilter(bloom);
    app.getViewPort().addProcessor(fpp);
  }

  @Override
  protected void cleanup(final Application app) {
    app.getViewPort().removeProcessor(fpp);
    fpp = null;
    bloom = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  public BloomFilter getBloom() {
    return bloom;
  }
}
