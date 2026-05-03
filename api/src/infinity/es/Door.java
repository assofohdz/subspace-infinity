// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

public class Door implements EntityComponent {

  private final long interval;
  private final long endTime;

  public Door() {
    this(0L, 0L);
  }

  public Door(final long createdTime, final long interval) {
    this.endTime = createdTime + interval;
    this.interval = interval;
  }

  public long getEndTime() {
    return endTime;
  }

  public long getInterval() {
    return interval;
  }
}
