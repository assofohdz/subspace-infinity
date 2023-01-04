package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

public class SphereShape implements EntityComponent {

    private double radius;

    protected SphereShape() {
    }

    public SphereShape(double radius){
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }
}
