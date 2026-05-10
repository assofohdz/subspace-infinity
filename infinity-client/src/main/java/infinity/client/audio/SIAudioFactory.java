// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author Asser
 */
public class SIAudioFactory implements AudioFactory {

    /**
     * Audio-type name → factory dispatch table. Replaces the long switch in
     * {@link #createAudio} with O(1) lookup. Each factory is an instance method
     * (it needs {@link #assets}), so entries are {@code Function<SIAudioFactory, AudioNode>}
     * called against {@code this} at dispatch time. Behaviour preserved: same
     * factory invoked for each {@link AudioTypes} key, same exception type and
     * message for unknown keys.
     */
    private static final Map<String, Function<SIAudioFactory, AudioNode>> FACTORIES =
        Map.ofEntries(
            Map.entry(AudioTypes.FIRE_THOR,       SIAudioFactory::fireThor),
            Map.entry(AudioTypes.PICKUP_PRIZE,    SIAudioFactory::pickupPrize),
            Map.entry(AudioTypes.FIRE_BOMBS_L1,   f -> f.fireBomb(1)),
            Map.entry(AudioTypes.FIRE_BOMBS_L2,   f -> f.fireBomb(2)),
            Map.entry(AudioTypes.FIRE_BOMBS_L3,   f -> f.fireBomb(3)),
            Map.entry(AudioTypes.FIRE_BOMBS_L4,   f -> f.fireBomb(4)),
            Map.entry(AudioTypes.FIRE_GUNS_L1,    f -> f.fireBullet(1)),
            Map.entry(AudioTypes.FIRE_GUNS_L2,    f -> f.fireBullet(2)),
            Map.entry(AudioTypes.FIRE_GUNS_L3,    f -> f.fireBullet(3)),
            Map.entry(AudioTypes.FIRE_GUNS_L4,    f -> f.fireBullet(4)),
            Map.entry(AudioTypes.FIRE_GRAVBOMB,   SIAudioFactory::fireGravBomb),
            Map.entry(AudioTypes.EXPLOSION2,      SIAudioFactory::explode),
            Map.entry(AudioTypes.BURST,           SIAudioFactory::fireBurst),
            Map.entry(AudioTypes.REPEL,           SIAudioFactory::createREPEL),
            Map.entry(AudioTypes.FLAG,            SIAudioFactory::pickupFlag),
            Map.entry(AudioTypes.FIRE_MINE_L1,    f -> f.placeMine(1)),
            Map.entry(AudioTypes.FIRE_MINE_L2,    f -> f.placeMine(2)),
            Map.entry(AudioTypes.FIRE_MINE_L3,    f -> f.placeMine(3)),
            Map.entry(AudioTypes.FIRE_MINE_L4,    f -> f.placeMine(4)));

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
        final String typeName = type.getTypeName(ed);
        final Function<SIAudioFactory, AudioNode> factory = FACTORIES.get(typeName);
        if (factory == null) {
            throw new UnsupportedOperationException("Unknown audio type:" + typeName);
        }
        return factory.apply(this);
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
            throw new UnsupportedOperationException("Unknown bullet level: " + gunLevel);
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
