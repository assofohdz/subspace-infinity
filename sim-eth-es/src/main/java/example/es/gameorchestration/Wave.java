package example.es.gameorchestration;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author Asser
 */
public class Wave implements EntityComponent{
    
    private final int wave;

    public Wave(int wave) {
        this.wave = wave;
    }

    public int getWave() {
        return wave;
    }
}
