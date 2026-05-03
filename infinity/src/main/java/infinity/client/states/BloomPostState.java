// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
