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
package infinity.client;

import com.jme3.input.KeyInput;

import com.simsilica.lemur.input.Button;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputState;

import infinity.es.ActionTypes;
import infinity.es.ShapeNames;
import infinity.es.ToggleTypes;
import infinity.es.WeaponTypes;

/**
 * Defines a set of player movement functions and their default control mappings.
 *
 * @author Paul Speed
 */
public class AvatarMovementFunctions {

  private AvatarMovementFunctions() {
    // Private constructor to prevent instantiation
  }

  public static final String G_MOVEMENT = "Movement";
  public static final String G_MAP = "Map";
  public static final String G_SHIPSELECTION = "Ship";
  public static final String G_TOWER = "Tower";
  public static final String G_TOGGLE = "Toggles";
  public static final String G_ACTION = "Actions";
  public static final String G_WEAPON = "Weapons";
  public static final FunctionId F_RUN = new FunctionId(G_MOVEMENT, "Afterburner");
  public static final FunctionId F_THRUST = new FunctionId(G_MOVEMENT, "Thrust");
  public static final FunctionId F_TURN = new FunctionId(G_MOVEMENT, "Turn");
  public static final FunctionId F_STOP = new FunctionId(G_MOVEMENT, "Stop");
  // Map
  public static final FunctionId F_MOUSE1 = new FunctionId(G_MAP, "UpdateTile");
  public static final FunctionId F_MOUSE2 = new FunctionId(G_MAP, "RemoveTile");
  public static final FunctionId F_MOUSE3 = new FunctionId(G_MAP, "Mouse3");
  // Weapons
  public static final FunctionId F_BOMB = new FunctionId(G_WEAPON, WeaponTypes.BOMB);
  public static final FunctionId F_GRAVBOMB = new FunctionId(G_WEAPON, WeaponTypes.GRAVITYBOMB);
  public static final FunctionId F_MINE = new FunctionId(G_WEAPON, WeaponTypes.MINE);
  public static final FunctionId F_SHOOT = new FunctionId(G_WEAPON, WeaponTypes.BULLET);
  public static final FunctionId F_THOR = new FunctionId(G_WEAPON, WeaponTypes.THOR);
  public static final FunctionId F_BURST = new FunctionId(G_WEAPON, WeaponTypes.BURST);
  // Actions
  public static final FunctionId F_REPEL = new FunctionId(G_ACTION, ActionTypes.REPEL);
  public static final FunctionId F_WARP = new FunctionId(G_ACTION, ActionTypes.WARP);
  public static final FunctionId F_PORTAL = new FunctionId(G_ACTION, ActionTypes.PORTAL);
  public static final FunctionId F_DECOY = new FunctionId(G_ACTION, ActionTypes.DECOY);
  public static final FunctionId F_ROCKET = new FunctionId(G_ACTION, ActionTypes.ROCKET);
  public static final FunctionId F_BRICK = new FunctionId(G_ACTION, ActionTypes.BRICK);
  public static final FunctionId F_ATTACH = new FunctionId(G_ACTION, ActionTypes.ATTACH);
  // Toggles
  public static final FunctionId F_MULTI = new FunctionId(G_TOGGLE, ToggleTypes.MULTI);
  public static final FunctionId F_ANTI = new FunctionId(G_TOGGLE, ToggleTypes.ANTI);
  public static final FunctionId F_STEALTH = new FunctionId(G_TOGGLE, ToggleTypes.STEALTH);
  public static final FunctionId F_CLOAK = new FunctionId(G_TOGGLE, ToggleTypes.CLOAK);
  public static final FunctionId F_XRADAR = new FunctionId(G_TOGGLE, ToggleTypes.XRADAR);
  // Tower defense
  public static final FunctionId F_TOWER = new FunctionId(G_TOWER, "Tower");
  // Ships
  public static final FunctionId F_WARBIRD =
      new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_WARBIRD);
  public static final FunctionId F_JAVELIN =
      new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_JAVELIN);
  public static final FunctionId F_SPIDER = new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_SPIDER);
  public static final FunctionId F_LEVI = new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_LEVI);
  public static final FunctionId F_TERRIER =
      new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_TERRIER);
  public static final FunctionId F_LANC =
      new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_LANCASTER);
  public static final FunctionId F_WEASEL = new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_WEASEL);
  public static final FunctionId F_SHARK = new FunctionId(G_SHIPSELECTION, ShapeNames.SHIP_SHARK);
  public static final String G_ALTERNATIVE = "Alternative";
  public static final FunctionId F_SHIFT = new FunctionId(G_ALTERNATIVE, "Shift");

  public static void initializeDefaultMappings(final InputMapper inputMapper) {

    // Default key mappings
    // Movement:--->>
    if (!inputMapper.hasMappings(F_SHIFT)) {
      inputMapper.map(F_SHIFT, KeyInput.KEY_LSHIFT);
    }

    if (!inputMapper.hasMappings(F_TURN)) {
      inputMapper.map(F_TURN, KeyInput.KEY_LEFT);
      inputMapper.map(F_TURN, InputState.Negative, KeyInput.KEY_RIGHT);
    }

    if (!inputMapper.hasMappings(F_THRUST)) {
      inputMapper.map(F_THRUST, KeyInput.KEY_UP);
      inputMapper.map(F_THRUST, InputState.Negative, KeyInput.KEY_DOWN);
    }

    if (!inputMapper.hasMappings(F_STOP)) {
      inputMapper.map(F_STOP, KeyInput.KEY_SPACE);
    }
    // <---

    /*
     * Mouse
     */
    if (!inputMapper.hasMappings(F_MOUSE1)) {
      inputMapper.map(F_MOUSE1, Button.MOUSE_BUTTON1);
    }

    if (!inputMapper.hasMappings(F_MOUSE2)) {
      inputMapper.map(F_MOUSE2, Button.MOUSE_BUTTON2);
    }

    if (!inputMapper.hasMappings(F_MOUSE3)) {
      inputMapper.map(F_MOUSE3, Button.MOUSE_BUTTON3);
    }
    /*
     * Actions
     */
    if (!inputMapper.hasMappings(F_WARP)) {
      inputMapper.map(F_WARP, KeyInput.KEY_INSERT);
    }
    /*
     * if (!inputMapper.hasMappings(F_REPEL)) { inputMapper.map(F_REPEL,
     * KeyInput.KEY_LSHIFT); }
     */
    /*
     * if (!inputMapper.hasMappings(F_PORTAL)) { inputMapper.map(F_REPEL,
     * KeyInput.KEY_LSHIFT, KeyInput.KEY_INSERT); }
     */
    if (!inputMapper.hasMappings(F_DECOY)) {
      inputMapper.map(F_REPEL, KeyInput.KEY_F5);
    }

    if (!inputMapper.hasMappings(F_ROCKET)) {
      inputMapper.map(F_REPEL, KeyInput.KEY_F3);
    }

    if (!inputMapper.hasMappings(F_BRICK)) {
      inputMapper.map(F_REPEL, KeyInput.KEY_F4);
    }

    if (!inputMapper.hasMappings(F_ATTACH)) {
      inputMapper.map(F_REPEL, KeyInput.KEY_F7);
    }

    /*
     * Weapons
     */
    if (!inputMapper.hasMappings(F_THOR)) {
      inputMapper.map(F_THOR, KeyInput.KEY_F12);
    }

    if (!inputMapper.hasMappings(F_GRAVBOMB)) {
      inputMapper.map(F_GRAVBOMB, KeyInput.KEY_BACKSLASH);
    }

    if (!inputMapper.hasMappings(F_GRAVBOMB)) {
      inputMapper.map(F_GRAVBOMB, KeyInput.KEY_BACKSLASH);
    }

    if (!inputMapper.hasMappings(F_MINE)) {
      inputMapper.map(F_MINE, KeyInput.KEY_TAB);
    }

    if (!inputMapper.hasMappings(F_SHOOT)) {
      inputMapper.map(F_SHOOT, KeyInput.KEY_LCONTROL);
    }

    if (!inputMapper.hasMappings(F_BOMB)) {
      inputMapper.map(F_BOMB, KeyInput.KEY_TAB);
    }

    if (!inputMapper.hasMappings(F_BURST)) {
      inputMapper.map(F_BURST, KeyInput.KEY_DELETE);
    }

    /*
     * Tower defense
     */
    if (!inputMapper.hasMappings(F_TOWER)) {
      inputMapper.map(F_TOWER, KeyInput.KEY_T);
    }

    /** Ship selection keys */
    if (!inputMapper.hasMappings(F_WARBIRD)) {
      inputMapper.map(F_WARBIRD, KeyInput.KEY_1);
    }
    if (!inputMapper.hasMappings(F_JAVELIN)) {
      inputMapper.map(F_JAVELIN, KeyInput.KEY_2);
    }
    if (!inputMapper.hasMappings(F_SPIDER)) {
      inputMapper.map(F_SPIDER, KeyInput.KEY_3);
    }
    if (!inputMapper.hasMappings(F_LEVI)) {
      inputMapper.map(F_LEVI, KeyInput.KEY_4);
    }
    if (!inputMapper.hasMappings(F_TERRIER)) {
      inputMapper.map(F_TERRIER, KeyInput.KEY_5);
    }
    if (!inputMapper.hasMappings(F_WEASEL)) {
      inputMapper.map(F_WEASEL, KeyInput.KEY_6);
    }
    if (!inputMapper.hasMappings(F_LANC)) {
      inputMapper.map(F_LANC, KeyInput.KEY_7);
    }
    if (!inputMapper.hasMappings(F_SHARK)) {
      inputMapper.map(F_SHARK, KeyInput.KEY_8);
    }
  }
}
