package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import org.ini4j.Ini;

public class ArenaSettings implements EntityComponent {

  private String arenaId;
  private Ini settings;

  public ArenaSettings() {
    //For serialization
  }

  public ArenaSettings(final String arenaId, final Ini settings) {
    this.arenaId = arenaId;
    this.settings = settings;
  }

  public String getArenaId() {
    return arenaId;
  }

  public Ini getSettings() {
    return settings;
  }
}
