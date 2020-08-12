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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;

import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.debug.BinStatusState;
import com.simsilica.ext.mphys.debug.BodyDebugState;
import com.simsilica.ext.mphys.debug.ContactDebugState;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.PhysicsStats;
import com.simsilica.state.CompositeAppState;
import com.simsilica.state.DebugHudState;

import infinity.HostState;

/**
 * When the physics is running locally, this state manages the various debug
 * views that are available.
 *
 * @author Paul Speed
 */
public class PhysicsDebugState extends CompositeAppState {

    static Logger log = LoggerFactory.getLogger(PhysicsDebugState.class);

    private HostState host;
    private PhysicsStats stats;

    private VersionedHolder<String> contacts;
    private VersionedHolder<String> frameTime;
    private VersionedHolder<String> binCount;
    private VersionedHolder<String> activeBinCount;
    private VersionedHolder<String> bodyCount;
    private VersionedHolder<String> activeBodyCount;

    public PhysicsDebugState(HostState host) {
        this.host = host;
    }

    @Override
    protected void initialize(Application app) {
        PhysicsSpace phys = host.getSystems().get(PhysicsSpace.class);
        stats = phys.getStats();
        addChild(new BinStatusState(phys, 64));
        addChild(new BodyDebugState(host.getSystems().get(MPhysSystem.class)));
        addChild(new ContactDebugState(phys));

        DebugHudState debug = getState(DebugHudState.class);
        if (debug != null) {
            frameTime = debug.createDebugValue("Phys Time", DebugHudState.Location.Right);
            contacts = debug.createDebugValue("Contacts", DebugHudState.Location.Right);
            binCount = debug.createDebugValue("Bins", DebugHudState.Location.Right);
            activeBinCount = debug.createDebugValue("Active Bins", DebugHudState.Location.Right);
            bodyCount = debug.createDebugValue("Bodies", DebugHudState.Location.Right);
            activeBodyCount = debug.createDebugValue("Active Bodies", DebugHudState.Location.Right);
        }

    }

    @Override
    protected void cleanup(Application app) {
        DebugHudState debug = getState(DebugHudState.class);
        if (debug != null) {
            debug.removeDebugValue("Contacts");
            debug.removeDebugValue("Phys Time");
            debug.removeDebugValue("Bins");
            debug.removeDebugValue("Active Bins");
            debug.removeDebugValue("Bodies");
            debug.removeDebugValue("Active Bodies");
        }
    }

    @Override
    public void update(float tpf) {
        // log.info("update");

        // We should be the last child of the GameSessionState... so everything
        // should be up-to-date.
        BinStatusState binState = getState(BinStatusState.class);
        if (binState != null) {
            Vector3f loc = getState(WorldViewState.class).getViewLocation();
            binState.setViewOrigin(loc.x, 0, loc.z);
            BodyDebugState bodyState = getState(BodyDebugState.class);
            bodyState.setViewOrigin(loc.x, 0, loc.z);
            ContactDebugState contactState = getState(ContactDebugState.class);
            contactState.setViewOrigin(loc.x, 0, loc.z);
        }

        if (contacts != null) {
            frameTime.setObject(String.format("%.2f ms", stats.getDouble(PhysicsStats.STAT_FRAME_TIME) / 1000000.0));
            contacts.setObject(String.valueOf(stats.getLong(PhysicsStats.STAT_CONTACTS)));
            binCount.setObject(String.valueOf(stats.getLong(PhysicsStats.STAT_BIN_COUNT)));
            activeBinCount.setObject(String.valueOf(stats.getLong(PhysicsStats.STAT_ACTIVE_BIN_COUNT)));
            bodyCount.setObject(String.valueOf(stats.getLong(PhysicsStats.STAT_BODY_COUNT)));
            activeBodyCount.setObject(String.valueOf(stats.getLong(PhysicsStats.STAT_ACTIVE_BODY_COUNT)));
        }
    }

    @Override
    protected void onEnable() {
        InputMapper input = GuiGlobals.getInstance().getInputMapper();
        input.addDelegate(DebugFunctions.F_BIN_DEBUG, getState(BinStatusState.class), "toggleEnabled");
        input.addDelegate(DebugFunctions.F_BODY_DEBUG, getState(BodyDebugState.class), "toggleEnabled");
        input.addDelegate(DebugFunctions.F_CONTACT_DEBUG, getState(ContactDebugState.class), "toggleEnabled");
    }

    @Override
    protected void onDisable() {
        InputMapper input = GuiGlobals.getInstance().getInputMapper();
        input.removeDelegate(DebugFunctions.F_BIN_DEBUG, getState(BinStatusState.class), "toggleEnabled");
        input.removeDelegate(DebugFunctions.F_BODY_DEBUG, getState(BodyDebugState.class), "toggleEnabled");
        input.removeDelegate(DebugFunctions.F_CONTACT_DEBUG, getState(ContactDebugState.class), "toggleEnabled");
    }
}
