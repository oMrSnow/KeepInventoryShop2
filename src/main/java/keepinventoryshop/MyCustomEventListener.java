package keepinventoryshop;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class MyCustomEventListener implements Listener {
    private final KeepInventoryShop plugin;

    public MyCustomEventListener(KeepInventoryShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Your event handling logic here
    }
}

