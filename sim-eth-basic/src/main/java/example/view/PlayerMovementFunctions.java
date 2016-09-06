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
import com.simsilica.lemur.input.Axis;
import com.simsilica.lemur.input.Button;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputState;


/**
 *  Defines a set of player movement functions and their default
 *  control mappings.
 *
 *  @author    Paul Speed
 */
public class PlayerMovementFunctions {

    public static final String G_MOVEMENT = "Movement";
    
    public static final FunctionId F_Y_ROTATE = new FunctionId(G_MOVEMENT, "Y Rotate");
    public static final FunctionId F_X_ROTATE = new FunctionId(G_MOVEMENT, "X Rotate");

    public static final FunctionId F_THRUST = new FunctionId(G_MOVEMENT, "Thrust");
    public static final FunctionId F_STRAFE = new FunctionId(G_MOVEMENT, "Strafe");
    public static final FunctionId F_ELEVATE = new FunctionId(G_MOVEMENT, "Elevate");
    
    public static final FunctionId F_BOOST = new FunctionId(G_MOVEMENT, "Boost");
 
    /**
     *  We capture some input mappings in case they need
     *  to be scaled or flipped later.  Mouse and joystick input are
     *  often configured with sensitivity settings and this is one
     *  way to do that built into InputMapper.
     */
    public static InputMapper.Mapping MOUSE_X_ROTATE;
    public static InputMapper.Mapping MOUSE_Y_ROTATE;
    public static InputMapper.Mapping JOY_X_ROTATE;
    public static InputMapper.Mapping JOY_Y_ROTATE;
    
    public static void initializeDefaultMappings( InputMapper inputMapper ) {
    
        if( !inputMapper.hasMappings(F_THRUST) ) {
            // The joystick Y axes are backwards on game pads... forward
            // is negative.  So we'll flip it over in the mapping.
            inputMapper.map(F_THRUST, InputState.Negative, Axis.JOYSTICK_LEFT_Y);
        
            // Here a similar approach is used to map the W and S keys with
            // 'S' being negative.  In this way, W and S now act like a joystick
            // axis.
            inputMapper.map(F_THRUST, KeyInput.KEY_W);
            inputMapper.map(F_THRUST, InputState.Negative, KeyInput.KEY_S);
        }
        
        if( !inputMapper.hasMappings(F_STRAFE) ) {
            // Strafing is setup similar to move.
            inputMapper.map(F_STRAFE, Axis.JOYSTICK_LEFT_X);
            inputMapper.map(F_STRAFE, KeyInput.KEY_D);
            inputMapper.map(F_STRAFE, InputState.Negative, KeyInput.KEY_A);
            
            inputMapper.map(F_STRAFE, Axis.JOYSTICK_HAT_X);
        }

        if( !inputMapper.hasMappings(F_ELEVATE) ) {
            // Elevation only has key mappings but we still treat it like
            // one "axis".
            inputMapper.map(F_ELEVATE, KeyInput.KEY_Q);
            inputMapper.map(F_ELEVATE, InputState.Negative, KeyInput.KEY_Z);
            
            inputMapper.map(F_ELEVATE, Axis.JOYSTICK_HAT_Y);
        } 

        if( !inputMapper.hasMappings(F_X_ROTATE) ) {
            // For the mouse and joystick mappings, we remember the mapping object
            // in case we want to flip it or adjust its sensitivity later.  This
            // is better than trying to do it in the function handler because it's
            // quite often input specific.  The user may want to flip the joystick
            // but not the mouse or adjust sensitivity on one and not the other.
            MOUSE_X_ROTATE = inputMapper.map(F_X_ROTATE, Axis.MOUSE_X);
            JOY_X_ROTATE = inputMapper.map(F_X_ROTATE, Axis.JOYSTICK_RIGHT_X);
            inputMapper.map(F_X_ROTATE, KeyInput.KEY_RIGHT);
            inputMapper.map(F_X_ROTATE, InputState.Negative, KeyInput.KEY_LEFT);
        } else {
            // Need to lookup the MOUSE_X_ROTATE and JOY_X_ROTATE if we
            // need them at some point
        }            

        if( !inputMapper.hasMappings(F_Y_ROTATE) ) {
            MOUSE_Y_ROTATE = inputMapper.map(F_Y_ROTATE, Axis.MOUSE_Y);
            JOY_Y_ROTATE = inputMapper.map(F_Y_ROTATE, Axis.JOYSTICK_RIGHT_Y);
            inputMapper.map(F_Y_ROTATE, KeyInput.KEY_UP);
            inputMapper.map(F_Y_ROTATE, InputState.Negative, KeyInput.KEY_DOWN);
        } else {
            // Need to lookup the MOUSE_Y_ROTATE and JOY_X_ROTATE if we
            // need them at some point
        }            

        if( !inputMapper.hasMappings(F_BOOST) ) {
            // Here we give run its own function.  Note that it was also possible
            // to treat running like an axis used instead of MOVE by combining
            // keys.  For example:  
            //   map(F_BOOST, KeyInput.KEY_LSHIFT, KeyInput.KEY_W )
            // Another approach would have been to set the 'scale' of the non-run
            // mapping to 0.5 or something and simply map SHIFT+W without the
            // scaling.  That would have done two things:
            // 1) the camera code simply needs to look for MOVE and not worry
            //      about a separate 'run mode' because walking will be half-speed
            //      move anyway.
            // 2) the joystick would always be running which means the player simply
            //      runs by using full stick.
            // 
            // Instead, I've opted for a run mode toggle.  Mostly because it demonstrates
            // state mapping where as all of the other inputs so far are going to
            // be treated as analog.  That means our joystick needs a run button also.       
            inputMapper.map(F_BOOST, KeyInput.KEY_LSHIFT);
            inputMapper.map(F_BOOST, Button.JOYSTICK_RIGHT1);
        }
    }
    
}    
