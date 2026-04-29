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

package infinity.config;

import java.util.List;

/**
 * Immutable, typed binding for zone-scope server config. Replaces the legacy
 * INI {@code zone.conf} as the source-of-truth for zone-wide settings; produced
 * by the Groovy config layer and read by zone-startup and connect-time spawn.
 *
 * @param autoLoadArenas arena folder names under {@code zone/arenas/} the
 *     server brings up on first tick (formerly {@code [Startup] AutoLoad=})
 * @param enterSpawnArena name of the loaded arena whose {@code [Spawn] X/Z}
 *     becomes the world-space spawn for new player sessions on first connect;
 *     blank means "spawn at world origin" (formerly
 *     {@code [ZoneEnterSpawn] Arena=})
 */
public record ZoneConfig(List<String> autoLoadArenas, String enterSpawnArena) {

  /**
   * Empty fallback installed if {@code zone.groovy} is missing or fails to
   * evaluate — matches the legacy INI behaviour of "no auto-load list, spawn at
   * world origin" so an unconfigured server still boots.
   */
  public static final ZoneConfig EMPTY = new ZoneConfig(List.of(), "");
}
