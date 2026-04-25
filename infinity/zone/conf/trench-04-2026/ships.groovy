/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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

// Per-arena ship config.
//
// GroovyShipLoader evaluates this file at arena load and installs the result
// as a ConfigRegistry snapshot via ConfigRegistrySystem.replace(). Stats use
// integer Subspace units (matches ship-<name> INI fragments); the ShipSpawn
// system converts to ECS-component units (e.g. rad/sec for Rotation) at spawn.
//
// DSL:
//   ship(Ship.WARBIRD) {
//       rotation initial: <I>, max: <M>, upgrade: <U>
//       thrust   initial: <I>, max: <M>, upgrade: <U>
//       speed    initial: <I>, max: <M>, upgrade: <U>
//       recharge initial: <I>, max: <M>, upgrade: <U>
//       energy   initial: <I>, max: <M>, upgrade: <U>
//       dragFactor          <D>     // 0..1 — fraction of Thrust applied as drag while coasting
//       turnResponsiveness  <R>     // 1/sec — angular ease rate; 8.0 ≈ ~95% of target in ~0.4 sec
//       bounceRestitution   <B>     // 0..1 — wall-bounce energy retention; 1.0 = perfectly elastic
//   }
//
// Omit a stat to leave it at ShipStat(0, 0, 0). The three feel knobs default to
// 0.05 / 8.0 / 1.0 (the historical Java globals) when omitted. MVP scope: Warbird only.

ship(Ship.WARBIRD) {
    rotation initial: 200,  max: 200,  upgrade: 0
    thrust   initial: 16,   max: 24,   upgrade: 0
    speed    initial: 2000, max: 6000, upgrade: 0
    recharge initial: 4000, max: 4000, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
}
