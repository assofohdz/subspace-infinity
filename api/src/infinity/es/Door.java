package infinity.es;

import com.simsilica.es.EntityComponent;

public class Door implements EntityComponent {

  private long interval;
  private long endTime;
  boolean open;

  public long getEndTime() {
    return endTime;
  }

  public Door() {
    //For serialization
  }

  public long getInterval() {
    return interval;
  }

  public boolean isOpen() {
    return open;
  }

  public Door ( long createdTime, long interval, boolean open) {
    this.endTime = createdTime + interval;
    this.interval = interval;
    this.open = open;
  }
}
