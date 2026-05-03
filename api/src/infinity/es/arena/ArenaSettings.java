// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import org.ini4j.Ini;

public class ArenaSettings implements EntityComponent {

  private final String arenaName;
  private final Ini settings;

  public ArenaSettings() {
    this(null, null);
  }

  public ArenaSettings(final String arenaName, final Ini settings) {
    this.arenaName = arenaName;
    this.settings = settings;
  }

  public String getArenaName() {
    return arenaName;
  }

  public Ini getSettings() {
    return settings;
  }
}
