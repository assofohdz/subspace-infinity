package infinity.es;

import com.simsilica.es.EntityComponent;

public class Door implements EntityComponent {

  private long interval;
  private long endTime;

  public long getEndTime() {
    return endTime;
  }

  public Door() {
    //For serialization
  }

  public long getInterval() {
    return interval;
  }


  public Door ( long createdTime, long interval) {
    this.endTime = createdTime + interval;
    this.interval = interval;
  }
}
