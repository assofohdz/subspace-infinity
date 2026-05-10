// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Maximum number of active Mines allowed to be dropped by a ship
 *
 * @author Asser
 */
public class MineMaxCount implements EntityComponent {

  private final int max;

  public MineMaxCount() {
    this(0);
  }

  public MineMaxCount(final int count) {
    max = count;
  }

  public int getCount() {
    return max;
  }
}
