/*
 * $Id$
 * 
 * Copyright (c) 2016, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package example.view;

import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.simsilica.lemur.input.Axis;
import com.simsilica.lemur.input.Button;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputState;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines a set of player movement functions and their default control
 * mappings.
 *
 * @author Paul Speed
 */
public class PlayerMovementFunctions {

    public static final String G_MOVEMENT = "Movement";
    public static final String G_MAP = "Map";

    public static final FunctionId F_THRUST = new FunctionId(G_MOVEMENT, "Thrust");

    /**
     * Turns the ship left or right. Default controls are setup as 'a' and 'd'.
     */
    public static final FunctionId F_TURN = new FunctionId(G_MOVEMENT, "Turn");

    /**
     * Shoots the pimary weapon of the ship. Default control mapping is the
     * space bar.
     */
    public static final FunctionId F_STOP = new FunctionId(G_MOVEMENT, "Stop");
    public static final FunctionId F_SHOOT = new FunctionId(G_MOVEMENT, "Shoot");
    public static final FunctionId F_MOUSELEFTCLICK = new FunctionId(G_MAP, "UpdateTile");
    public static final FunctionId F_MOUSERIGHTCLICK = new FunctionId(G_MAP, "RemoveTile");
    public static final FunctionId F_BOMB = new FunctionId(G_MOVEMENT, "Bomb");

    public static void initializeDefaultMappings(InputMapper inputMapper) {

        // Default key mappings
        if (!inputMapper.hasMappings(F_TURN)) {
            inputMapper.map(F_TURN, KeyInput.KEY_A);
            inputMapper.map(F_TURN, InputState.Negative, KeyInput.KEY_D);

        }

        if (!inputMapper.hasMappings(F_THRUST)) {
            inputMapper.map(F_THRUST, KeyInput.KEY_W);
            inputMapper.map(F_THRUST, InputState.Negative, KeyInput.KEY_S);
        }

        if (!inputMapper.hasMappings(F_SHOOT)) {
            inputMapper.map(F_SHOOT, KeyInput.KEY_LCONTROL);
        }

        if (!inputMapper.hasMappings(F_BOMB)) {
            inputMapper.map(F_BOMB, KeyInput.KEY_TAB);
        }

        if (!inputMapper.hasMappings(F_STOP)) {
            inputMapper.map(F_STOP, KeyInput.KEY_SPACE);
        }
        
        if (!inputMapper.hasMappings(F_MOUSELEFTCLICK)) {
            inputMapper.map(F_MOUSELEFTCLICK, Button.MOUSE_BUTTON1);
        }
        
        if (!inputMapper.hasMappings(F_MOUSERIGHTCLICK)) {
            inputMapper.map(F_MOUSERIGHTCLICK, Button.MOUSE_BUTTON2);
        }
    }
}
