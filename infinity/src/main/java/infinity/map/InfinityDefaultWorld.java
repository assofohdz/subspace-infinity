/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.map;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.base.DefaultWorld;
import com.simsilica.mworld.db.LeafDb;
import java.util.HashMap;

/**
 *
 * @author AFahrenholz
 */
public class InfinityDefaultWorld extends DefaultWorld {

    HashMap<Vec3d, String> cellToSet;

    public InfinityDefaultWorld(LeafDb leafDb) {
        super(leafDb);
        
        cellToSet = new HashMap<>();
    }

    public int setWorldCell(Vec3d world, String set, int type) {

        cellToSet.put(world, set);

        return super.setWorldCell(world, type);
    }

    public TileKey getInfinityWorldCell(Vec3d world) {

        int result = super.getWorldCell(world);

        String set = cellToSet.get(world);

        return new TileKey(result, set);
    }

    public class TileKey {

        int type;
        String set;

        public TileKey(int type, String set) {
            this.type = type;
            this.set = set;
        }

        public int getType() {
            return type;
        }

        public String getSet() {
            return set;
        }
    }
}
