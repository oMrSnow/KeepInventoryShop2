package keepinventoryshop;

import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
public class KeepInventoryFlag extends StateFlag {

    public KeepInventoryFlag(String name, boolean def, RegionGroup defaultGroup) {
        super(name, def, defaultGroup);
    }
}
