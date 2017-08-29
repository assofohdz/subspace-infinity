/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author ss
 */
public class TowerRotationSpeed implements EntityComponent {
    double radSec ;
    
    public TowerRotationSpeed(){
        
    }
    public TowerRotationSpeed(double radSec){
        this.radSec = radSec;
    }
    
    public double getRadSec(){
        return this.radSec;
    }
}
