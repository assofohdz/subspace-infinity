/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.subspace;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import example.es.WeaponType;

/**
 *
 * @author Asser
 */
public class PrizeType implements EntityComponent {

    private int type;

    protected PrizeType() {
    }

    public PrizeType(int type) {
        this.type = type;
    }

    public static PrizeType create(String typeName, EntityData ed) {
        return new PrizeType(ed.getStrings().getStringId(typeName, true));
    }

    public int getType() {
        return type;
    }

    public String getTypeName(EntityData ed) {
        return ed.getStrings().getString(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + "]";
    }
}
