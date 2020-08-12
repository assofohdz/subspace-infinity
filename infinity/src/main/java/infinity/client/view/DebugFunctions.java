/*
 * $Id$
 *
 * Copyright (c) 2019, Simsilica, LLC
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

package infinity.client.view;

import com.jme3.input.KeyInput;

import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;

/**
 * Defines some in-game debug toggles.
 *
 * @author Paul Speed
 */
public class DebugFunctions {

    public static final String IN_GAME = "In Game";

    public static final FunctionId F_BIN_DEBUG = new FunctionId(IN_GAME, "Bin Status Toggle");
    public static final FunctionId F_BODY_DEBUG = new FunctionId(IN_GAME, "Body Debug Toggle");
    public static final FunctionId F_CONTACT_DEBUG = new FunctionId(IN_GAME, "Contact Debug Toggle");

    public static void initializeDefaultMappings(final InputMapper inputMapper) {

        inputMapper.map(F_BIN_DEBUG, KeyInput.KEY_F3);
        inputMapper.map(F_BODY_DEBUG, KeyInput.KEY_F4);
        inputMapper.map(F_CONTACT_DEBUG, KeyInput.KEY_F4, Integer.valueOf(KeyInput.KEY_LSHIFT));
        inputMapper.map(F_CONTACT_DEBUG, KeyInput.KEY_F4, Integer.valueOf(KeyInput.KEY_RSHIFT));
    }
}
