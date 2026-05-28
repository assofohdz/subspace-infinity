// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.ZoneBotAiConfig;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Evaluates {@code zone-bot-ai.groovy} → {@link ZoneBotAiConfig}. Returns {@link ZoneBotAiConfig#DEFAULTS} on any failure (logged). See ADR-0013 / ADR-0014. */
public class GroovyZoneBotAiLoader {

  public static final String DEFAULT_PATH = "/zone-bot-ai.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyZoneBotAiLoader.class);
  private static final ZoneBotAiAdapter ADAPTER = new ZoneBotAiAdapter();

  public ZoneBotAiConfig load() {
    return load(DEFAULT_PATH);
  }

  public ZoneBotAiConfig load(final String classpathPath) {
    final ZoneBotAiConfig raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final ZoneBotAiConfig cfg = raw == null ? ZoneBotAiConfig.DEFAULTS : raw;
    if (cfg != ZoneBotAiConfig.DEFAULTS && log.isInfoEnabled()) {
      log.info(
          "Applied {}: plannerCadenceMillis={}, stickinessMargin={}, minFraction={}",
          classpathPath,
          cfg.plannerCadenceMillis(),
          cfg.stickinessMargin(),
          cfg.minFraction());
    }
    return cfg;
  }

  private static final class ZoneBotAiAdapter
      implements GroovySettingsAdapter<ZoneBotAiConfig, ZoneBotAiConfigBuilder> {

    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public ZoneBotAiConfigBuilder bind(final Binding binding) {
      final ZoneBotAiConfigBuilder builder = new ZoneBotAiConfigBuilder();
      binding.setVariable("botAi", new BotAiClosure(builder));
      return builder;
    }

    @Override
    public ZoneBotAiConfig extract(final ZoneBotAiConfigBuilder accumulator) {
      return accumulator.build();
    }

    @Override
    public ZoneBotAiConfig empty() {
      return ZoneBotAiConfig.DEFAULTS;
    }
  }

  private static final class BotAiClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient ZoneBotAiConfigBuilder builder;

    BotAiClosure(final ZoneBotAiConfigBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(builder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /** Delegate for {@code botAi{…}}. */
  public static final class ZoneBotAiConfigBuilder {

    private long plannerCadenceMillis = ZoneBotAiConfig.DEFAULTS.plannerCadenceMillis();
    private double stickinessMargin = ZoneBotAiConfig.DEFAULTS.stickinessMargin();
    private double minFraction = ZoneBotAiConfig.DEFAULTS.minFraction();
    private double minBehaviourWeight = ZoneBotAiConfig.DEFAULTS.minBehaviourWeight();
    private long navFieldTtlMs = ZoneBotAiConfig.DEFAULTS.navFieldTtlMs();
    private int navMaxFields = ZoneBotAiConfig.DEFAULTS.navMaxFields();
    private long densityCadenceMillis = ZoneBotAiConfig.DEFAULTS.densityCadenceMillis();
    private int densityKernelRadius = ZoneBotAiConfig.DEFAULTS.densityKernelRadius();
    private int threatRadius = ZoneBotAiConfig.DEFAULTS.threatRadius();
    private int opportunityRadius = ZoneBotAiConfig.DEFAULTS.opportunityRadius();
    private double combatDecayPerCadence = ZoneBotAiConfig.DEFAULTS.combatDecayPerCadence();
    private int chokepointTopN = ZoneBotAiConfig.DEFAULTS.chokepointTopN();
    private int chokepointMaxWidth = ZoneBotAiConfig.DEFAULTS.chokepointMaxWidth();
    private double chokepointDensityWeight = ZoneBotAiConfig.DEFAULTS.chokepointDensityWeight();
    private double navThreatWeight = ZoneBotAiConfig.DEFAULTS.navThreatWeight();
    private double navOpportunityWeight = ZoneBotAiConfig.DEFAULTS.navOpportunityWeight();
    private boolean flowFieldDebug = ZoneBotAiConfig.DEFAULTS.flowFieldDebug();
    private int flowFieldDebugRadius = ZoneBotAiConfig.DEFAULTS.flowFieldDebugRadius();
    private double engagementRangeUnits = ZoneBotAiConfig.DEFAULTS.engagementRangeUnits();
    private double bountyReference = ZoneBotAiConfig.DEFAULTS.bountyReference();
    private double supportRadiusUnits = ZoneBotAiConfig.DEFAULTS.supportRadiusUnits();
    private double isolationReference = ZoneBotAiConfig.DEFAULTS.isolationReference();
    private double threatReference = ZoneBotAiConfig.DEFAULTS.threatReference();
    private double lookAheadDistance = ZoneBotAiConfig.DEFAULTS.lookAheadDistance();
    private double corridorHalfWidth = ZoneBotAiConfig.DEFAULTS.corridorHalfWidth();
    private double avoidThrust = ZoneBotAiConfig.DEFAULTS.avoidThrust();
    private double oversteerThrustFloor = ZoneBotAiConfig.DEFAULTS.oversteerThrustFloor();
    private int wallRepulsionRadius = ZoneBotAiConfig.DEFAULTS.wallRepulsionRadius();
    private double wallObstacleRadius = ZoneBotAiConfig.DEFAULTS.wallObstacleRadius();
    private int navGoalSnapRadius = ZoneBotAiConfig.DEFAULTS.navGoalSnapRadius();
    private int navHullFootprintCells = ZoneBotAiConfig.DEFAULTS.navHullFootprintCells();
    private int combatSplatRadius = ZoneBotAiConfig.DEFAULTS.combatSplatRadius();
    private double searchIdleFit = ZoneBotAiConfig.DEFAULTS.searchIdleFit();
    private double searchEngagedFit = ZoneBotAiConfig.DEFAULTS.searchEngagedFit();
    private double holdPositionIdleFit = ZoneBotAiConfig.DEFAULTS.holdPositionIdleFit();
    private double holdPositionEngagedFit = ZoneBotAiConfig.DEFAULTS.holdPositionEngagedFit();
    private double followTrafficIdleFit = ZoneBotAiConfig.DEFAULTS.followTrafficIdleFit();
    private double followTrafficEngagedFit = ZoneBotAiConfig.DEFAULTS.followTrafficEngagedFit();
    private double followTrafficDistDecayCells =
        ZoneBotAiConfig.DEFAULTS.followTrafficDistDecayCells();

    ZoneBotAiConfigBuilder() {}

    /** Min wall-time (ms) between planner re-selects; must be {@code > 0}. */
    public void plannerCadenceMillis(final Number value) {
      if (value == null) {
        return;
      }
      final long v = value.longValue();
      if (v <= 0L) {
        throw new IllegalArgumentException("plannerCadenceMillis must be > 0; got " + value);
      }
      this.plannerCadenceMillis = v;
    }

    /** Additive preempt margin in weighted-score units; {@code [0,1]}. */
    public void stickinessMargin(final Number value) {
      this.stickinessMargin = unitFraction("stickinessMargin", value, this.stickinessMargin);
    }

    /** Adaptive enumeration floor as a fraction of the top weight; {@code (0,1]}. */
    public void minFraction(final Number value) {
      this.minFraction = unitFraction("minFraction", value, this.minFraction);
    }

    /** Absolute weight floor dropping near-zero synergy bonuses; {@code [0,1]}. */
    public void minBehaviourWeight(final Number value) {
      this.minBehaviourWeight = unitFraction("minBehaviourWeight", value, this.minBehaviourWeight);
    }

    /** Idle-timeout (ms) before a transient flow field is evicted; must be {@code > 0}. */
    public void navFieldTtlMs(final Number value) {
      if (value == null) {
        return;
      }
      final long v = value.longValue();
      if (v <= 0L) {
        throw new IllegalArgumentException("navFieldTtlMs must be > 0; got " + value);
      }
      this.navFieldTtlMs = v;
    }

    /** LRU cap on transient flow fields per arena; must be {@code > 0}. */
    public void navMaxFields(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("navMaxFields must be > 0; got " + value);
      }
      this.navMaxFields = v;
    }

    /** Min wall-time (ms) between team-density-field rebuilds; must be {@code > 0}. */
    public void densityCadenceMillis(final Number value) {
      if (value == null) {
        return;
      }
      final long v = value.longValue();
      if (v <= 0L) {
        throw new IllegalArgumentException("densityCadenceMillis must be > 0; got " + value);
      }
      this.densityCadenceMillis = v;
    }

    /** Per-ship density splat radius in tile cells; must be {@code > 0}. */
    public void densityKernelRadius(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("densityKernelRadius must be > 0; got " + value);
      }
      this.densityKernelRadius = v;
    }

    /** Per-ship threat (weapon-range) falloff radius in tile cells; must be {@code > 0}. */
    public void threatRadius(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("threatRadius must be > 0; got " + value);
      }
      this.threatRadius = v;
    }

    /** Per-prize opportunity splat radius in tile cells; must be {@code > 0}. */
    public void opportunityRadius(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("opportunityRadius must be > 0; got " + value);
      }
      this.opportunityRadius = v;
    }

    /** Combat-heatmap fade per cadence; {@code (0,1)} exclusive. */
    public void combatDecayPerCadence(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || v <= 0.0 || v >= 1.0) {
        throw new IllegalArgumentException("combatDecayPerCadence must be in (0,1); got " + value);
      }
      this.combatDecayPerCadence = v;
    }

    /** Number of chokepoint tiles pinned as static nav goals; must be {@code > 0}. */
    public void chokepointTopN(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("chokepointTopN must be > 0; got " + value);
      }
      this.chokepointTopN = v;
    }

    /** Max corridor width (tile cells) for a chokepoint pinch; must be {@code > 0}. */
    public void chokepointMaxWidth(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("chokepointMaxWidth must be > 0; got " + value);
      }
      this.chokepointMaxWidth = v;
    }

    /** Weight on position-density in the chokepoint hotness combo; {@code [0,1]}. */
    public void chokepointDensityWeight(final Number value) {
      this.chokepointDensityWeight =
          unitFraction("chokepointDensityWeight", value, this.chokepointDensityWeight);
    }

    /** Weight on threat-avoidance blended into the nav heading; {@code [0,1]}. */
    public void navThreatWeight(final Number value) {
      this.navThreatWeight = unitFraction("navThreatWeight", value, this.navThreatWeight);
    }

    /** Weight on opportunity-seeking blended into the nav heading; {@code [0,1]}. */
    public void navOpportunityWeight(final Number value) {
      this.navOpportunityWeight =
          unitFraction("navOpportunityWeight", value, this.navOpportunityWeight);
    }

    /** Dev-only flow-field debug overlay sampling on/off. */
    public void flowFieldDebug(final boolean value) {
      this.flowFieldDebug = value;
    }

    /** Half-extent (tile cells) of the sampled flow patch around a player; must be {@code > 0}. */
    public void flowFieldDebugRadius(final Number value) {
      if (value == null) {
        return;
      }
      final int v = value.intValue();
      if (v <= 0) {
        throw new IllegalArgumentException("flowFieldDebugRadius must be > 0; got " + value);
      }
      this.flowFieldDebugRadius = v;
    }

    /** ADR-0016 {@code range_opt}: nominal optimal engage distance (world units); must be {@code > 0}. */
    public void engagementRangeUnits(final Number value) {
      this.engagementRangeUnits = positive("engagementRangeUnits", value, this.engagementRangeUnits);
    }

    /** ADR-0016 {@code B_ref}: bounty saturating {@code bounty_pull} to 1.0; must be {@code > 0}. */
    public void bountyReference(final Number value) {
      this.bountyReference = positive("bountyReference", value, this.bountyReference);
    }

    /** ADR-0016 {@code R}: ally-count radius (world units) for {@code support}; must be {@code > 0}. */
    public void supportRadiusUnits(final Number value) {
      this.supportRadiusUnits = positive("supportRadiusUnits", value, this.supportRadiusUnits);
    }

    /** ADR-0016 {@code iso_ref}: target-isolation saturation distance (world units); must be {@code > 0}. */
    public void isolationReference(final Number value) {
      this.isolationReference = positive("isolationReference", value, this.isolationReference);
    }

    /** ADR-0016 {@code threat_ref}: enemy-threat field value saturating threat inputs; must be {@code > 0}. */
    public void threatReference(final Number value) {
      this.threatReference = positive("threatReference", value, this.threatReference);
    }

    /** Reactive-steering forward look-ahead (world units); must be {@code > 0}. */
    public void lookAheadDistance(final Number value) {
      this.lookAheadDistance = positive("lookAheadDistance", value, this.lookAheadDistance);
    }

    /** Half-width of the {@code AvoidObstacles} corridor (world units); must be {@code > 0}. */
    public void corridorHalfWidth(final Number value) {
      this.corridorHalfWidth = positive("corridorHalfWidth", value, this.corridorHalfWidth);
    }

    /** Thrust when reactive avoid / wall-repel overrides BT steering; must be {@code > 0}. */
    public void avoidThrust(final Number value) {
      this.avoidThrust = positive("avoidThrust", value, this.avoidThrust);
    }

    /** Min thrust multiplier at max turn rate; must be in {@code [0,1]}. */
    public void oversteerThrustFloor(final Number value) {
      this.oversteerThrustFloor =
          unitFraction("oversteerThrustFloor", value, this.oversteerThrustFloor);
    }

    /** Grid wall-repulsion reach in tile cells (1 = adjacent only); must be {@code >= 1}. */
    public void wallRepulsionRadius(final Number value) {
      this.wallRepulsionRadius = positiveInt("wallRepulsionRadius", value, this.wallRepulsionRadius);
    }

    /** Perception wall-hit obstacle radius (world units); must be {@code > 0}. */
    public void wallObstacleRadius(final Number value) {
      this.wallObstacleRadius = positive("wallObstacleRadius", value, this.wallObstacleRadius);
    }

    /** Async flow-field goal snap radius (tile cells); must be {@code >= 1}. */
    public void navGoalSnapRadius(final Number value) {
      this.navGoalSnapRadius = positiveInt("navGoalSnapRadius", value, this.navGoalSnapRadius);
    }

    /** Routing-grid Minkowski hull-footprint erosion (tile cells); must be {@code >= 1}. */
    public void navHullFootprintCells(final Number value) {
      this.navHullFootprintCells =
          positiveInt("navHullFootprintCells", value, this.navHullFootprintCells);
    }

    /** Combat heatmap splat radius (tile cells); must be {@code >= 1}. */
    public void combatSplatRadius(final Number value) {
      this.combatSplatRadius = positiveInt("combatSplatRadius", value, this.combatSplatRadius);
    }

    /** {@code SearchBehaviour} idle fit; in {@code [0,1]}. */
    public void searchIdleFit(final Number value) {
      this.searchIdleFit = unitFraction("searchIdleFit", value, this.searchIdleFit);
    }

    /** {@code SearchBehaviour} engaged fit; in {@code [0,1]}. */
    public void searchEngagedFit(final Number value) {
      this.searchEngagedFit = unitFraction("searchEngagedFit", value, this.searchEngagedFit);
    }

    /** {@code HoldPositionBehaviour} idle fit; in {@code [0,1]}. */
    public void holdPositionIdleFit(final Number value) {
      this.holdPositionIdleFit = unitFraction("holdPositionIdleFit", value, this.holdPositionIdleFit);
    }

    /** {@code HoldPositionBehaviour} engaged fit; in {@code [0,1]}. */
    public void holdPositionEngagedFit(final Number value) {
      this.holdPositionEngagedFit =
          unitFraction("holdPositionEngagedFit", value, this.holdPositionEngagedFit);
    }

    /** {@code FollowTrafficBehaviour} idle fit; in {@code [0,1]}. */
    public void followTrafficIdleFit(final Number value) {
      this.followTrafficIdleFit =
          unitFraction("followTrafficIdleFit", value, this.followTrafficIdleFit);
    }

    /** {@code FollowTrafficBehaviour} engaged fit; in {@code [0,1]}. */
    public void followTrafficEngagedFit(final Number value) {
      this.followTrafficEngagedFit =
          unitFraction("followTrafficEngagedFit", value, this.followTrafficEngagedFit);
    }

    /** {@code FollowTraffic} hot-tile distance falloff scale (tile cells); must be {@code > 0}. */
    public void followTrafficDistDecayCells(final Number value) {
      this.followTrafficDistDecayCells =
          positive("followTrafficDistDecayCells", value, this.followTrafficDistDecayCells);
    }

    private static int positiveInt(final String key, final Number value, final int current) {
      if (value == null) {
        return current;
      }
      final int v = value.intValue();
      if (v < 1) {
        throw new IllegalArgumentException(key + " must be >= 1; got " + value);
      }
      return v;
    }

    private static double positive(final String key, final Number value, final double current) {
      if (value == null) {
        return current;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(key + " must be > 0; got " + value);
      }
      return v;
    }

    private static double unitFraction(
        final String key, final Number value, final double current) {
      if (value == null) {
        return current;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0 || v > 1.0) {
        throw new IllegalArgumentException(key + " must be in [0,1]; got " + value);
      }
      return v;
    }

    ZoneBotAiConfig build() {
      return new ZoneBotAiConfig(
          plannerCadenceMillis,
          stickinessMargin,
          minFraction,
          minBehaviourWeight,
          navFieldTtlMs,
          navMaxFields,
          densityCadenceMillis,
          densityKernelRadius,
          threatRadius,
          opportunityRadius,
          combatDecayPerCadence,
          chokepointTopN,
          chokepointMaxWidth,
          chokepointDensityWeight,
          navThreatWeight,
          navOpportunityWeight,
          flowFieldDebug,
          flowFieldDebugRadius,
          engagementRangeUnits,
          bountyReference,
          supportRadiusUnits,
          isolationReference,
          threatReference,
          lookAheadDistance,
          corridorHalfWidth,
          avoidThrust,
          oversteerThrustFloor,
          wallRepulsionRadius,
          wallObstacleRadius,
          navGoalSnapRadius,
          navHullFootprintCells,
          combatSplatRadius,
          searchIdleFit,
          searchEngagedFit,
          holdPositionIdleFit,
          holdPositionEngagedFit,
          followTrafficIdleFit,
          followTrafficEngagedFit,
          followTrafficDistDecayCells);
    }
  }
}
