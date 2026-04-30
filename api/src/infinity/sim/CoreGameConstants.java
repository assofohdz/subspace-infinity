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

package infinity.sim;

/**
 * Time must be specified in milliseconds
 *
 * @author Asser Fahrenholz
 */
public class CoreGameConstants {

  public static int GRAVBOMBDAMAGE = 10;
  public static int BOMBDAMAGE = 10;
  public static int BULLETDAMAGE = 10;
  public static int THORDAMAGE = 10;

  private CoreGameConstants() {
    // Private constructor to prevent instantiation
  }

  // Projectile speeds
  public static final double BASEPROJECTILESPEED = 1;
  public static final double BOMBPROJECTILESPEED = 25;
  public static final double BULLETPROJECTILESPEED = 50;
  public static final double GRAVBOMBPROJECTILESPEED = 15;
  public static final double THORPROJECTILESPEED = 30;
  public static final double BURSTPROJECTILESPEED = 50;

  // Decays must be in milliseconds
  public static final long BULLETDECAY = 1500;
  public static final long PRIZEDECAY = 20000;
  public static final long THORDECAY = 1500;
  public static final long GRAVBOMBDECAY = 4000;
  public static final long MINEDECAY = 10000;

  // Health
  public static final int SHIPHEALTH = 100;
  public static final int BASEHEALTH = 1000;
  public static final int MOBHEALTH = 100;

  // Cooldowns
  // GUNCOOLDOWN / BOMBCOOLDOWN / MINECOOLDOWN moved to per-arena ShipConfig
  // (GunStats.fireDelayCs / BombStats.fireDelayCs / MineStats.fireDelayCs)
  // — see config-pattern.md. THORCOOLDOWN / BURSTCOOLDOWN are still
  // referenced by WeaponsSystem and pending the same migration.
  public static final long THORCOOLDOWN = 500;
  public static final long BURSTCOOLDOWN = 250;

  // Cost of firing
  // GUNCOST / BOMBCOST / MINECOST moved to per-arena ShipConfig
  // (GunStats.cost / BombStats.cost / MineStats.cost) — see
  // config-pattern.md.

  public static final long BURSTPROJECTILECOUNT = 30;

  public static final int BOUNTYVALUE = 10;
  public static final int PRIZEMAXCOUNT = 10;

  public static final long GRAVBOMBDELAY = 1000;
  public static final double GRAVBOMBWORMHOLEFORCE = 5000;

  public static final double RESOURCE_UPDATE_INTERVAL = 1;
  public static final double GOLD_PER_SECOND = 10000;
  public static final float PATHWAYPOINTDISTANCE = 0.5f;
  public static final double MOBSPEED = 100;
  public static final double MOBMAXFORCE = 200;
  public static final float PATHHELPERHEIGHT = 1000;
  public static final float PATHHELPERWIDTH = 1000;

  public static final int TOWERCOST = 1000;

  public static final String DEFAULTARENAID = "default";
  public static final String BOMBLEVELPREPENDTEXT = "bomb_l";
  public static final String BULLETLEVELPREPENDTEXT = "bullet_l";

  public static final double UPDATE_SETTINGS_INTERVAL_MS = 1000;
  public static final String MINELEVELPREPENDTEXT = "mine_l";
}
