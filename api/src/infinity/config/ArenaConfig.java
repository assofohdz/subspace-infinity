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
 * Immutable, typed binding for the per-arena top-level settings — the keys that
 * used to live at the top of {@code arena.conf} before any {@code #include}.
 * Produced by the Groovy config layer (or synthesised from INI when a legacy
 * arena hasn't been migrated yet) and read by the arena-load and spawn paths.
 *
 * <p>Out of scope for this binding: the contents of {@code includeFragment}
 * targets — those still flow through {@code SettingsSystem}'s INI store
 * unchanged. {@code fragmentIncludes} is the list of paths to load, not the
 * data inside them.
 *
 * @param mapFile classpath-relative {@code .lvl} path (formerly
 *     {@code [General] Map=})
 * @param shipsScript classpath-absolute path to the ship Groovy script
 *     (formerly {@code [Scripts] Ships=}); blank means "no script — install
 *     the {@code GroovyShipLoader.FALLBACK} snapshot"
 * @param spawnX arena-local X for first-spawn / respawn (formerly
 *     {@code [Spawn] X=})
 * @param spawnZ arena-local Z for first-spawn / respawn (formerly
 *     {@code [Spawn] Z=})
 * @param fragmentIncludes paths the arena's settings come from (formerly the
 *     {@code #include} directives at the bottom of {@code arena.conf}); each
 *     path is loaded individually through the existing INI loader, with its
 *     own {@code #include} support
 */
public record ArenaConfig(
    String mapFile,
    String shipsScript,
    int spawnX,
    int spawnZ,
    List<String> fragmentIncludes) {

  /**
   * Empty fallback — a clean ArenaConfig with no map / ships / spawn / fragments.
   * Used by callers that need a non-null default before the real config is
   * assembled, mirroring {@link ZoneConfig#EMPTY}.
   */
  public static final ArenaConfig EMPTY = new ArenaConfig("", "", 0, 0, List.of());
}
