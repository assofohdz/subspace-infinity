/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.client.audio;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.audio.plugins.WAVLoader;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;

import infinity.client.ConnectionState;
import infinity.es.AudioType;
import infinity.es.AudioTypes;
import infinity.es.ship.weapons.BombLevelEnum;
import infinity.es.ship.weapons.GunLevelEnum;

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
        audioState = state;
        assets = state.getApplication().getAssetManager();
        ed = state.getApplication().getStateManager().getState(ConnectionState.class).getEntityData();
        assets.registerLoader(WAVLoader.class, "wa2");
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
            return createFIRE_BOMB(e, BombLevelEnum.BOMB_1);
        case AudioTypes.FIRE_GUNS_L1:
        case AudioTypes.FIRE_GUNS_L2:
        case AudioTypes.FIRE_GUNS_L3:
        case AudioTypes.FIRE_GUNS_L4:
            return createFIRE_BULLET(e, GunLevelEnum.LEVEL_1);
        case AudioTypes.FIRE_GRAVBOMB:
            return createFIRE_GRAVBOMB(e);
        case AudioTypes.EXPLOSION2:
            return createEXPLOSION2(e);
        case AudioTypes.BURST:
            return createFIRE_BURST(e);
        case AudioTypes.REPEL:
            return createREPEL(e);
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

    private AudioNode createFIRE_BOMB(Entity e, BombLevelEnum bombLevel) {
        String sound = "";
        switch (bombLevel.level) {
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
            break;
        default:
            throw new UnsupportedOperationException("Unknown bomb level: " + bombLevel.level);
        }
        AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_BULLET(Entity e, GunLevelEnum gunLevel) {
        String sound = "";
        switch (gunLevel.level) {
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
            break;
        default:
            throw new UnsupportedOperationException("Unknown gun level: " + gunLevel.level);
        }
        AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_THOR(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/thor.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createPICKUP_PRIZE(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/prize.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_GRAVBOMB(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/thor.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createEXPLOSION2(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/explode2.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createFIRE_BURST(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/burst.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createREPEL(Entity e) {
        AudioNode an = new AudioNode(assets, "Sounds/Subspace/repel.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }
}
