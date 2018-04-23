/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.audio;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.audio.plugins.WAVLoader;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import example.ConnectionState;
import example.es.AudioType;
import example.es.AudioTypes;
import example.es.ViewTypes;
import example.es.ship.weapons.BombLevel;
import example.es.ship.weapons.GunLevel;

/**
 *
 * @author Asser
 */
public class SIAudioFactory implements AudioFactory {

    private AudioState audioState;
    private EntityData ed;
    private AssetManager assets;

    @Override
    public void setState(AudioState state) {
        this.audioState = state;
        this.assets = state.getApplication().getAssetManager();
        this.ed = state.getApplication().getStateManager().getState(ConnectionState.class).getEntityData();
        this.assets.registerLoader(WAVLoader.class, "wa2");
    }

    @Override
    public AudioNode createAudio(Entity e) {
        AudioType type = e.get(AudioType.class);

        switch (type.getTypeName(ed)) {
            case AudioTypes.FIRE_THOR:
                return createFIRE_THOR(e);
            case AudioTypes.PICKUP_PRIZE:
                return createPICKUP_PRIZE(e);
            case AudioTypes.FIRE_BOMBS_L1:
            case AudioTypes.FIRE_BOMBS_L2:
            case AudioTypes.FIRE_BOMBS_L3:
            case AudioTypes.FIRE_BOMBS_L4:
                return createFIRE_BOMB(e, BombLevel.BOMB_1);
            case AudioTypes.FIRE_GUNS_L1:
            case AudioTypes.FIRE_GUNS_L2:
            case AudioTypes.FIRE_GUNS_L3:
            case AudioTypes.FIRE_GUNS_L4:
                return createFIRE_BULLET(e, GunLevel.LEVEL_1);
            case AudioTypes.FIRE_GRAVBOMB:
                return createFIRE_GRAVBOMB(e);
            default:
                throw new UnsupportedOperationException("Unknown audio type:" + type.getTypeName(ed));
        }

    }

    private void setDefaults(AudioNode an) {
        an.setPositional(true);
        an.setLooping(false);
        an.setVolume(1);
    }

    private void setStreamingDefaults(AudioNode an) {
        an.setPositional(true);
        an.setLooping(true);
        an.setVolume(1);
    }

    private AudioNode createFIRE_BOMB(Entity e, BombLevel bombLevel) {
        String sound = "";
        switch(bombLevel.level){
            case 1:
                sound = "Sounds/Subspace/bomb1.wa2";
                break;
            case 2:
                sound = "Sounds/Subspace/bomb2.wa2";
                break;
            case 3:
                sound = "Sounds/Subspace/bomb3.wa2";
                break;
            case 4: 
                sound = "Sounds/Subspace/bomb4.wa2";
            default:
                throw new UnsupportedOperationException("Unknown bomb level: "+bombLevel.level);
        }
        AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        this.setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_BULLET(Entity e, GunLevel gunLevel) {
        String sound = "";
        switch(gunLevel.level){
            case 1:
                sound = "Sounds/Subspace/gun1.wa2";
                break;
            case 2:
                sound = "Sounds/Subspace/gun2.wa2";
                break;
            case 3:
                sound = "Sounds/Subspace/gun3.wa2";
                break;
            case 4: 
                sound = "Sounds/Subspace/gun4.wa2";
            default:
                throw new UnsupportedOperationException("Unknown gun level: "+gunLevel.level);
        }
        AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        this.setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_THOR(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/thor.wa2", AudioData.DataType.Buffer);
        this.setDefaults(an);
        return an;
    }

    private AudioNode createPICKUP_PRIZE(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/prize.wa2", AudioData.DataType.Buffer);
        this.setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_GRAVBOMB(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/thor.wa2", AudioData.DataType.Buffer);
        this.setDefaults(an);
        return an;
    }
}
