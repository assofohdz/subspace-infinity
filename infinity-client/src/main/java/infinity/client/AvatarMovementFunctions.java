// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client;

import com.jme3.input.KeyInput;
import java.util.LinkedHashMap;
import java.util.Map;

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
  public static final FunctionId F_ROCKET = new FunctionId(G_ACTION, ActionTypes.ROCKET);
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
    mapMovement(inputMapper);
    mapMouse(inputMapper);
    mapActions(inputMapper);
    mapWeapons(inputMapper);
    mapTower(inputMapper);
    mapShipSelection(inputMapper);
  }

  private static void mapMovement(final InputMapper inputMapper) {
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
  }

  private static void mapMouse(final InputMapper inputMapper) {
    if (!inputMapper.hasMappings(F_MOUSE1)) {
      inputMapper.map(F_MOUSE1, Button.MOUSE_BUTTON1);
    }

    if (!inputMapper.hasMappings(F_MOUSE2)) {
      inputMapper.map(F_MOUSE2, Button.MOUSE_BUTTON2);
    }

    if (!inputMapper.hasMappings(F_MOUSE3)) {
      inputMapper.map(F_MOUSE3, Button.MOUSE_BUTTON3);
    }
  }

  private static void mapActions(final InputMapper inputMapper) {
    if (!inputMapper.hasMappings(F_WARP)) {
      inputMapper.map(F_WARP, KeyInput.KEY_INSERT);
    }

    // Subspace canon: shift = repel. F_SHIFT (alt-modifier for shift+TAB→mine,
    // tracked in AvatarMovementState.shiftPressed) is bound to LSHIFT, so bind
    // repel on RSHIFT to avoid firing a repel every frame while the alt-modifier
    // is held.
    if (!inputMapper.hasMappings(F_REPEL)) {
      inputMapper.map(F_REPEL, KeyInput.KEY_RSHIFT);
    }

    // F_ROCKET on KEY_R for Mac users (KEY_END requires Fn+Right on Mac
    // laptops). KEY_END kept as a second binding for canonical hardware
    // keyboards where End is a dedicated key.
    if (!inputMapper.hasMappings(F_ROCKET)) {
      inputMapper.map(F_ROCKET, KeyInput.KEY_R);
      inputMapper.map(F_ROCKET, KeyInput.KEY_END);
    }
  }

  private static void mapWeapons(final InputMapper inputMapper) {
    if (!inputMapper.hasMappings(F_THOR)) {
      inputMapper.map(F_THOR, KeyInput.KEY_F12);
    }

    if (!inputMapper.hasMappings(F_GRAVBOMB)) {
      inputMapper.map(F_GRAVBOMB, KeyInput.KEY_BACKSLASH);
    }

    if (!inputMapper.hasMappings(F_SHOOT)) {
      inputMapper.map(F_SHOOT, KeyInput.KEY_LCONTROL);
    }

    if (!inputMapper.hasMappings(F_BOMB)) {
      // Subspace / Continuum canon: TAB fires bombs. Slice S2 + bomb-recoil
      // pairing — moving off SPACE also resolves the pre-existing
      // SPACE-vs-F_STOP key conflict for the bomb-fire path (F_STOP keeps
      // SPACE).
      inputMapper.map(F_BOMB, KeyInput.KEY_TAB);
    }

    if (!inputMapper.hasMappings(F_BURST)) {
      inputMapper.map(F_BURST, KeyInput.KEY_DELETE);
    }
  }

  private static void mapTower(final InputMapper inputMapper) {
    if (!inputMapper.hasMappings(F_TOWER)) {
      inputMapper.map(F_TOWER, KeyInput.KEY_T);
    }
  }

  /** Ship selection keys: F_WARBIRD..F_SHARK → KEY_1..KEY_8. */
  private static final Map<FunctionId, Integer> SHIP_SELECTION_KEYS = buildShipSelectionKeys();

  private static Map<FunctionId, Integer> buildShipSelectionKeys() {
    // LinkedHashMap preserves the canonical 1..8 order for diagnostics.
    final Map<FunctionId, Integer> m = new LinkedHashMap<>();
    m.put(F_WARBIRD, KeyInput.KEY_1);
    m.put(F_JAVELIN, KeyInput.KEY_2);
    m.put(F_SPIDER, KeyInput.KEY_3);
    m.put(F_LEVI, KeyInput.KEY_4);
    m.put(F_TERRIER, KeyInput.KEY_5);
    m.put(F_WEASEL, KeyInput.KEY_6);
    m.put(F_LANC, KeyInput.KEY_7);
    m.put(F_SHARK, KeyInput.KEY_8);
    return Map.copyOf(m);
  }

  /** Ship selection keys */
  private static void mapShipSelection(final InputMapper inputMapper) {
    SHIP_SELECTION_KEYS.forEach((func, key) -> {
      if (!inputMapper.hasMappings(func)) {
        inputMapper.map(func, key);
      }
    });
  }
}
