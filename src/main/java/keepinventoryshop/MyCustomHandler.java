package keepinventoryshop;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class MyCustomHandler extends Handler implements Listener {
    private final KeepInventoryShop plugin;

    public static class Factory extends Handler.Factory<MyCustomHandler> {
        private final KeepInventoryShop plugin;

        public Factory(KeepInventoryShop plugin) {
            this.plugin = plugin;
        }

        @Override
        public MyCustomHandler create(Session session) {
            return new MyCustomHandler(plugin, session);
        }
    }

    public MyCustomHandler(KeepInventoryShop plugin, Session session) {
        super(session, KeepInventoryShop.MY_CUSTOM_FLAG);
        this.plugin = plugin;
    }
    public boolean getFlagValue(Player player) {
        WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(player.getWorld());

        ApplicableRegionSet regions = regionManager.getApplicableRegions(player.getLocation());
        return regions.queryValue(WorldGuard.getInstance().wrapPlayer(player), KeepInventoryShop.MY_CUSTOM_FLAG);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();

        // Check if the player is in a region with the custom flag set
        com.sk89q.worldguard.protection.flags.StateFlag.State flagValue = worldGuard.getFlagValue(player.getLocation(), player, KeepInventoryShop.MY_CUSTOM_FLAG);

        if (flagValue != null) {
            if (flagValue == com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW) {
                // KeepInventory flag is set to true, do not drop items and do not consume a life
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.setDroppedExp(0);
                event.getDrops().clear();
            } else if (flagValue == com.sk89q.worldguard.protection.flags.StateFlag.State.DENY) {
                // KeepInventory flag is set to false, drop items and do not consume a life
                event.setKeepInventory(false);
            }
        }
    }
}
