/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.audio;

import com.jme3.audio.AudioNode;
import com.simsilica.es.Entity;

/**
 *
 * @author Asser
 */
public interface AudioFactory {

    /**
     *
     * @param state
     */
    public void setState(AudioState state);
    
    public AudioNode createAudio(Entity e);

}
