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

/**
 *
 * @author Asser
 */
public class SIAudioFactory implements AudioFactory {

    private EntityData ed;
    private AssetManager assets;

    @Override
    public void setState(final AudioState state) {
        assets = state.getApplication().getAssetManager();
        ed = state.getApplication().getStateManager().getState(ConnectionState.class).getEntityData();
        assets.registerLoader(WAVLoader.class, "wa2");
    }

    @Override
    public AudioNode createAudio(final Entity e) {
        final AudioType type = e.get(AudioType.class);

        switch (type.getTypeName(ed)) {
        case AudioTypes.FIRE_THOR:
            return fireThor();
        case AudioTypes.PICKUP_PRIZE:
            return pickupPrize();
        case AudioTypes.FIRE_BOMBS_L1:
            return fireBomb(1);
        case AudioTypes.FIRE_BOMBS_L2:
            return fireBomb(2);
        case AudioTypes.FIRE_BOMBS_L3:
            return fireBomb(3);
        case AudioTypes.FIRE_BOMBS_L4:
            return fireBomb(4);
        case AudioTypes.FIRE_GUNS_L1:
            return fireBullet(1);
        case AudioTypes.FIRE_GUNS_L2:
            return fireBullet(2);
        case AudioTypes.FIRE_GUNS_L3:
            return fireBullet(3);
        case AudioTypes.FIRE_GUNS_L4:
            return fireBullet(4);
        case AudioTypes.FIRE_GRAVBOMB:
            return fireGravBomb();
        case AudioTypes.EXPLOSION2:
            return explode();
        case AudioTypes.BURST:
            return fireBurst();
        case AudioTypes.REPEL:
            return createREPEL();
        case AudioTypes.FLAG:
            return pickupFlag();
        case AudioTypes.FIRE_MINE_L1:
            return placeMine(1);
        case AudioTypes.FIRE_MINE_L2:
            return placeMine(2);
        case AudioTypes.FIRE_MINE_L3:
            return placeMine(3);
        case AudioTypes.FIRE_MINE_L4:
            return placeMine(4);
        default:
            throw new UnsupportedOperationException("Unknown audio type:" + type.getTypeName(ed));
        }

    }

    private AudioNode placeMine(int i) {
        String sound = "";
        switch (i) {
        case 1:
            sound = "Sounds/Subspace/mine1.wa2";
            break;
        case 2:
            sound = "Sounds/Subspace/mine2.wa2";
            break;
        case 3:
            sound = "Sounds/Subspace/mine3.wa2";
            break;
        case 4:
            sound = "Sounds/Subspace/mine4.wa2";
            break;
        default:
            throw new UnsupportedOperationException("Unknown mine level: " + i);
        }
        final AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private void setDefaults(final AudioNode an) {
        an.setPositional(true);
        an.setLooping(false);
        an.setVolume(1);
    }

    @SuppressWarnings("unused")
    private void setStreamingDefaults(final AudioNode an) {
        an.setPositional(true);
        an.setLooping(true);
        an.setVolume(1);
    }

    private AudioNode fireBomb(int bombLevel) {
        String sound = "";
        switch (bombLevel) {
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
            throw new UnsupportedOperationException("Unknown bomb level: " + bombLevel);
        }
        final AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode fireBullet(int gunLevel) {
        String sound = "";
        switch (gunLevel) {
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
            throw new UnsupportedOperationException("Unknown gun level: " + gunLevel);
        }
        final AudioNode an = new AudioNode(assets, sound, AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode fireThor() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/thor.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    //A method to play the flag.wa2 sound
    private AudioNode pickupFlag() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/flag.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode pickupPrize() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/prize.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode fireGravBomb() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/bomb.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode explode() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/explode2.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode fireBurst() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/burst.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }

    private AudioNode createREPEL() {
        final AudioNode an = new AudioNode(assets, "Sounds/Subspace/repel.wa2", AudioData.DataType.Buffer);
        setDefaults(an);
        return an;
    }
}
