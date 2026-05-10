// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.audio;

import com.jme3.audio.AudioNode;

import com.simsilica.es.Entity;

/**
 *
 * @author Asser
 */
public interface AudioFactory {

    /**
     * References the parent audio state
     *
     * @param state the parent audio state to set
     */
    void setState(AudioState state);

    AudioNode createAudio(Entity e);

}
