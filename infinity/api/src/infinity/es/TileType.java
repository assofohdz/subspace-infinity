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
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 * Indicates the type of tile. Could be Legacy or Wang Blob
 *
 * @author Paul Speed
 */
public class TileType implements EntityComponent {

    private int type;
    private String tileSet;
    private short tileIndex;

    public TileType(int type, String tileSet, short tileIndex) {
        this.type = type;
        this.tileSet = tileSet;
        this.tileIndex = tileIndex;
    }

    protected TileType() {
    }

    public static TileType create(String typeName, String tileSet, short tileIndex, EntityData ed) {
        return new TileType(ed.getStrings().getStringId(typeName, true), tileSet, tileIndex);
    }

    public int getType() {
        return type;
    }

    public String getTypeName(EntityData ed) {
        return ed.getStrings().getString(type);
    }
    
    public TileType newTileIndex(short newTileIndex, EntityData ed){
        return new TileType(type, tileSet, newTileIndex);
    }

    public String getTileSet() {
        return tileSet;
    }

    public short getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return "TileType{" + "type=" + type + ", tileSet=" + tileSet + ", tileIndex=" + tileIndex + '}';
    }
}
