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
package infinity.es;

import com.simsilica.es.EntityComponent;
import java.util.Map;

/**
 * Optional per-spawner override for prize-type weights. Sparse map merged on
 * top of the spawner's arena defaults at selection time by {@code
 * PrizeSystem}. Keys are prize-type strings (matching {@code PrizeTypes}
 * constants — {@code "Bomb"}, {@code "Gun"}, ...); values are non-negative
 * weights. Authored declaratively via {@code arena.groovy}'s
 * {@code prizeSpawners { spawn ..., weights: [...] }} block (see
 * {@code PrizeSpawnerSpec.weightOverrides}).
 *
 * <p>Server-only — like {@code Spawner} itself, prize spawners aren't visible
 * to clients, so no network serializer registration is needed.
 *
 * @author Asser
 */
public class PrizeWeightsOverride implements EntityComponent {

  private final Map<String, Integer> overrides;

  public PrizeWeightsOverride() {
    this(Map.of());
  }

  public PrizeWeightsOverride(final Map<String, Integer> overrides) {
    this.overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
  }

  /** Unmodifiable view of the overrides. Never {@code null}; may be empty. */
  public Map<String, Integer> getOverrides() {
    return overrides;
  }
}
