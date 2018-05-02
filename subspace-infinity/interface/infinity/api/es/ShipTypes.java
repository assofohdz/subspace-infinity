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
package infinity.api.es;

import com.simsilica.es.EntityData;

/**
 *
 * @author Asser
 */
public class ShipTypes {

    public static final String WARBIRD = "warbird";
    public static final String JAVELIN = "javeling";
    public static final String SPIDER = "spider";
    public static final String LEVIATHAN = "leviathan";
    public static final String TERRIER = "terrier";
    public static final String WEASEL = "weasel";
    public static final String LANCASTER = "lancaster";
    public static final String SHARK = "shark";

    public static ShipType warbird(EntityData ed) {
        return ShipType.create(WARBIRD, ed);
    }

    public static ShipType javelin(EntityData ed) {
        return ShipType.create(JAVELIN, ed);
    }

    public static ShipType spider(EntityData ed) {
        return ShipType.create(SPIDER, ed);
    }

    public static ShipType leviathan(EntityData ed) {
        return ShipType.create(LEVIATHAN, ed);
    }

    public static ShipType terrier(EntityData ed) {
        return ShipType.create(TERRIER, ed);
    }

    public static ShipType weasel(EntityData ed) {
        return ShipType.create(WEASEL, ed);
    }

    public static ShipType lancaster(EntityData ed) {
        return ShipType.create(LANCASTER, ed);
    }

    public static ShipType shark(EntityData ed) {
        return ShipType.create(SHARK, ed);
    }
}
