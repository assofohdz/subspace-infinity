// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
 * @param spawnX arena-local X for the player spawn point in this arena
 *     (formerly {@code [Spawn] X=}). Default {@code 512} — arena center —
 *     when {@code arena.groovy} omits the {@code spawn} directive.
 * @param spawnZ arena-local Z for the player spawn point in this arena
 *     (formerly {@code [Spawn] Z=}). Default {@code 512} — arena center —
 *     when {@code arena.groovy} omits the {@code spawn} directive.
 * @param fragmentIncludes paths the arena's settings come from (formerly the
 *     {@code #include} directives at the bottom of {@code arena.conf}); each
 *     path is loaded individually through the existing INI loader, with its
 *     own {@code #include} support
 * @param wallFriction per-contact fraction of the body's <i>tangential</i>
 *     velocity removed when sliding along a static map block. Applied directly
 *     to the body's linear velocity (NOT via the rigid-body resolver's
 *     friction model — that produces a torque at off-center contact points,
 *     which would rotate ship heading toward the wall and is wrong for
 *     arcade-style ship physics). {@code 0.0} (default) keeps walls
 *     frictionless: glancing hits slide along the wall with no energy loss.
 *     Higher values progressively drain tangential velocity per contact tick,
 *     so {@code 0.05}–{@code 0.10} feels like noticeable slowdown over a
 *     short slide; {@code 0.5} drops most tangential velocity within a few
 *     frames. Values must be in {@code [0, 1]} (validated at parse time).
 *     Normal-direction velocity (the bounce) is untouched here — that stays
 *     under {@code BounceRestitution}'s control.
 * @param spawners declarative spawner entries materialized at arena-load
 *     by {@code ArenaSystem.doLoad}. Empty list means "no per-arena
 *     spawners"; the legacy hardcoded spawner in
 *     {@code BasicEnvironment} continues to run independently.
 * @param friendlyFire tri-state friendly-fire policy:
 *     {@code 0} = off (no weapons damage same-team ships),
 *     {@code 1} = bomb splash only (only bomb AoE damages teammates; bullets,
 *     burst, mines pass through teammates without damage),
 *     {@code 2} = all weapons damage teammates.
 *     Default {@code 0}. This is an Infinity-specific knob (Subspace canon
 *     models friendly fire with separate per-weapon flags); the tri-state
 *     here is a deliberate divergence chosen for slice-9a scope. Consumed
 *     by {@code WeaponsSystem.newContact}.
 */
public record ArenaConfig(
    String mapFile,
    String shipsScript,
    int spawnX,
    int spawnZ,
    List<String> fragmentIncludes,
    double wallFriction,
    List<SpawnerSpec> spawners,
    int friendlyFire) {

  /**
   * Empty fallback — a clean ArenaConfig with no map / ships / fragments,
   * the player spawn at the arena's centre tile {@code (512, 512)}, the
   * historical {@code wallFriction = 0.0} (frictionless walls), and
   * {@code friendlyFire = 0} (off). Used by callers that need a non-null
   * default before the real config is assembled, mirroring
   * {@link ZoneConfig#EMPTY}.
   */
  public static final ArenaConfig EMPTY =
      new ArenaConfig("", "", 512, 512, List.of(), 0.0, List.of(), 0);
}
