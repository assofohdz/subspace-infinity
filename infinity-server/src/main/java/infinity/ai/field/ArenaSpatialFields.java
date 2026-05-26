// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.Bin;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import infinity.ai.field.chokepoint.ChokepointAnalyzer;
import infinity.ai.field.combat.CombatDensityField;
import infinity.ai.field.density.ArenaDensity;
import infinity.ai.field.nav.AsyncNavigationFields;
import infinity.ai.field.opportunity.OpportunityField;
import infinity.ai.field.threat.ArenaThreat;
import infinity.config.ZoneBotAiConfig;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.PrizeType;
import infinity.es.WeaponType;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BotShip;
import infinity.es.ship.PlayerShip;
import infinity.systems.MapSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-arena spatial-field production for the bot-AI substrate (ADR-0011 / ADR-0012). Owns the
 * flow-field nav builder thread, the lazy per-arena {@link ArenaNav} holders, and the dynamic-field
 * refresh (density / threat / opportunity / combat) on the density cadence. Extracted from
 * {@code BotBrainSystem} so the brain reads finished {@link ArenaNav}s without owning the
 * gather/refresh/build plumbing. Not a {@code BaseInfinitySystem} — owned + driven by
 * {@code BotBrainSystem} so the brain has synchronous per-arena access each tick.
 */
public final class ArenaSpatialFields {

  private static final Logger log = LoggerFactory.getLogger(ArenaSpatialFields.class);

  // Sentinel for "fields have never refreshed" — the first cadence check refreshes unconditionally.
  private static final long UNREFRESHED = Long.MIN_VALUE;

  private final EntityData ed;
  private final PhysicsSpace<EntityId, MBlockShape> space;
  private final MapSystem mapSystem;
  private final Supplier<ZoneBotAiConfig> zoneCfg;

  // One flow-field nav per arena, built lazily from MapSystem passability; Dijkstra runs on this
  // single worker thread off the sim tick. Rebuilt when the arena's passability grid is replaced.
  private ExecutorService navBuilder;
  private final Map<String, ArenaNav> navByArena = new HashMap<>();
  private long lastDensityNanos = UNREFRESHED;
  private EntitySet prizes;
  private EntitySet projectiles;

  public ArenaSpatialFields(
      final EntityData ed,
      final PhysicsSpace<EntityId, MBlockShape> space,
      final MapSystem mapSystem,
      final Supplier<ZoneBotAiConfig> zoneCfg) {
    this.ed = ed;
    this.space = space;
    this.mapSystem = mapSystem;
    this.zoneCfg = zoneCfg;
  }

  public void start() {
    this.navBuilder =
        Executors.newSingleThreadExecutor(
            r -> {
              final Thread t = new Thread(r, "bot-nav-builder");
              t.setDaemon(true);
              return t;
            });
    this.prizes = this.ed.getEntities(PrizeType.class, SpawnPosition.class);
    this.projectiles = this.ed.getEntities(WeaponType.class, SpawnPosition.class);
  }

  public void stop() {
    if (this.navBuilder != null) {
      this.navBuilder.shutdownNow();
      this.navBuilder = null;
    }
    if (this.prizes != null) {
      this.prizes.release();
      this.prizes = null;
    }
    if (this.projectiles != null) {
      this.projectiles.release();
      this.projectiles = null;
    }
    this.navByArena.clear();
    this.lastDensityNanos = UNREFRESHED;
  }

  /** Per-tick: refresh dynamic fields on cadence, then idle-evict transient flow fields (pinned goals survive). */
  public void refresh(final long nowNanos) {
    refreshDynamicFields(nowNanos);
    for (final ArenaNav arenaNav : this.navByArena.values()) {
      arenaNav.fields().evictExpired();
    }
  }

  /**
   * Rebuild every arena's dynamic spatial fields on the density cadence (ADR-0012): per-team density
   * + threat from one active-bin ship pass, opportunity from the prize set + combat (fire) heatmap
   * from new projectiles this cadence, both assigned to arenas by map-bounds (neither carry an
   * {@link ArenaId}). Combat decays then accumulates; the others are full rebuilds.
   */
  private void refreshDynamicFields(final long nowNanos) {
    if (this.navByArena.isEmpty()) {
      return;
    }
    final long cadenceNanos = this.zoneCfg.get().densityCadenceMillis() * 1_000_000L;
    if (!shouldRefresh(this.lastDensityNanos, nowNanos, cadenceNanos)) {
      return;
    }
    this.lastDensityNanos = nowNanos;
    final Map<String, Map<Integer, List<int[]>>> shipsByArenaFreq = gatherShipCells();
    final List<int[]> prizeCells = gatherPrizeCells();
    final List<int[]> shotCells = gatherNewShotCells();
    for (final Map.Entry<String, ArenaNav> e : this.navByArena.entrySet()) {
      final ArenaNav nav = e.getValue();
      final Map<Integer, List<int[]>> ships = shipsByArenaFreq.getOrDefault(e.getKey(), Map.of());
      nav.density().rebuild(ships);
      nav.threat().rebuild(ships);
      nav.opportunity().rebuild(prizesInArena(prizeCells, nav));
      nav.combat().decay();
      for (final int[] shot : shotCells) {
        final int rx = shot[0] - nav.originX();
        final int rz = shot[1] - nav.originZ();
        if (rx >= 0 && rz >= 0 && rx < nav.width() && rz < nav.height()) {
          nav.combat().addShot(shot[0], shot[1]);
        }
      }
    }
  }

  /**
   * Whether the dynamic fields should refresh this tick: the first run ({@link #UNREFRESHED}) always
   * refreshes; otherwise the cadence must have elapsed. The {@code == UNREFRESHED} short-circuit runs
   * before the subtraction so {@code nowNanos - Long.MIN_VALUE} can't overflow into a spurious
   * "not yet". Mirrors {@code BotBrainSystem.shouldPlan} for the field cadence.
   */
  private static boolean shouldRefresh(
      final long last, final long nowNanos, final long cadenceNanos) {
    return last == UNREFRESHED || nowNanos - last >= cadenceNanos;
  }

  /** Fire-origin world-cells of projectiles spawned since the last cadence (new shots = combat heat). */
  private List<int[]> gatherNewShotCells() {
    this.projectiles.applyChanges();
    final List<int[]> cells = new ArrayList<>();
    for (final Entity e : this.projectiles.getAddedEntities()) {
      final Vec3d loc = e.get(SpawnPosition.class).getLocation();
      cells.add(new int[] {(int) Math.floor(loc.x), (int) Math.floor(loc.z)});
    }
    return cells;
  }

  /** Prize world-cells falling inside this arena's grid box ({@code [origin, origin+dims)}). */
  private static List<int[]> prizesInArena(final List<int[]> prizeCells, final ArenaNav nav) {
    final List<int[]> in = new ArrayList<>();
    for (final int[] cell : prizeCells) {
      final int rx = cell[0] - nav.originX();
      final int rz = cell[1] - nav.originZ();
      if (rx >= 0 && rz >= 0 && rx < nav.width() && rz < nav.height()) {
        in.add(cell);
      }
    }
    return in;
  }

  /**
   * One pass over the physics active bins, grouping live ship world-cells by arena then by freq.
   * Active-bin scoped (not every body) so distant/asleep zones cost nothing; ships are bodies
   * carrying {@link BotShip} or {@link PlayerShip} and a {@link Frequency}.
   */
  private Map<String, Map<Integer, List<int[]>>> gatherShipCells() {
    final Map<String, Map<Integer, List<int[]>>> out = new HashMap<>();
    for (final Bin<EntityId, MBlockShape> bin : this.space.getBinIndex().getActiveBins()) {
      for (final RigidBody<EntityId, MBlockShape> body : bin.getActiveObjects().getArray()) {
        final EntityId id = body.id;
        final boolean isShip =
            this.ed.getComponent(id, BotShip.class) != null
                || this.ed.getComponent(id, PlayerShip.class) != null;
        if (!isShip || this.ed.getComponent(id, Dead.class) != null) {
          continue;
        }
        final ArenaId arenaId = this.ed.getComponent(id, ArenaId.class);
        final Frequency freq = this.ed.getComponent(id, Frequency.class);
        if (arenaId == null || freq == null) {
          continue;
        }
        out.computeIfAbsent(arenaId.getArena(), a -> new HashMap<>())
            .computeIfAbsent(freq.getFrequency(), f -> new ArrayList<>())
            .add(new int[] {(int) Math.floor(body.position.x), (int) Math.floor(body.position.z)});
      }
    }
    return out;
  }

  /** All prize world-cells this cadence (arena assignment happens later by map-bounds). */
  private List<int[]> gatherPrizeCells() {
    this.prizes.applyChanges();
    final List<int[]> cells = new ArrayList<>(this.prizes.size());
    for (final Entity e : this.prizes) {
      final Vec3d loc = e.get(SpawnPosition.class).getLocation();
      cells.add(new int[] {(int) Math.floor(loc.x), (int) Math.floor(loc.z)});
    }
    return cells;
  }

  /**
   * The arena's nav holder, built lazily and rebuilt when {@link MapSystem} hands back a new
   * passability grid (map swap). Chokepoint top-N are pinned as static flow-field goals at build.
   */
  // CompareObjectsWithEquals: grid reference identity = "map changed" (cheaper than, and not
  // equivalent to, a deep equals — MapSystem swaps the grid reference atomically on map change).
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  public ArenaNav forArena(final String arena, final boolean[][] passable) {
    ArenaNav existing = this.navByArena.get(arena);
    if (existing == null || existing.grid() != passable) {
      final ZoneBotAiConfig cfg = this.zoneCfg.get();
      final AsyncNavigationFields fields =
          new AsyncNavigationFields(
              passable,
              this.navBuilder,
              System::nanoTime,
              cfg.navFieldTtlMs() * 1_000_000L,
              cfg.navMaxFields());
      final Vec3d min = this.mapSystem.getMapBoundsMin(arena);
      final int originX = (int) Math.floor(min.x);
      final int originZ = (int) Math.floor(min.z);
      final int height = passable.length;
      final int width = height == 0 ? 0 : passable[0].length;
      final ArenaDensity density =
          new ArenaDensity(width, height, originX, originZ, cfg.densityKernelRadius());
      final ArenaThreat threat =
          new ArenaThreat(width, height, originX, originZ, cfg.threatRadius(), passable);
      final OpportunityField opportunity =
          new OpportunityField(width, height, originX, originZ, cfg.opportunityRadius());
      final CombatDensityField combat =
          new CombatDensityField(
              width, height, originX, originZ, cfg.densityKernelRadius(), cfg.combatDecayPerCadence());
      // Pool extra geometric candidates so runtime heatmap re-ranking has material; pin the top-N
      // (geometric, traffic-independent — static flow-field goals built at arena load).
      final int topN = cfg.chokepointTopN();
      final List<TileScored> chokepoints =
          new ChokepointAnalyzer(passable, cfg.chokepointMaxWidth()).hottest(topN * 3);
      for (int i = 0; i < Math.min(topN, chokepoints.size()); i++) {
        fields.pin(chokepoints.get(i).x(), chokepoints.get(i).y());
      }
      existing =
          new ArenaNav(
              passable, fields, density, threat, opportunity, combat, chokepoints, originX, originZ,
              width, height);
      this.navByArena.put(arena, existing);
      if (log.isInfoEnabled()) {
        log.info(
            "bot-nav grid built for arena '{}': {}x{} cells, {} passable, {} chokepoints ({} pinned)",
            arena,
            width,
            height,
            countPassable(passable),
            chokepoints.size(),
            Math.min(topN, chokepoints.size()));
      }
    }
    return existing;
  }

  private static int countPassable(final boolean[][] grid) {
    int n = 0;
    for (final boolean[] row : grid) {
      for (final boolean cell : row) {
        if (cell) {
          n++;
        }
      }
    }
    return n;
  }
}
