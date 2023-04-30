package keepinventoryshop;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class KeepInventoryReloadCommand implements CommandExecutor {
    private KeepInventoryShop plugin;

    public KeepInventoryReloadCommand(KeepInventoryShop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("keepinventoryshop.reload")) {
            this.plugin.reloadPluginConfiguration();
            int costPerLife = this.plugin.getConfig().getInt("cost-per-life", 500);
            this.plugin.setCostPerLife(costPerLife);
            sender.sendMessage(ChatColor.GREEN + "KeepInventoryShop configuration reloaded.");
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        }
        return true;
    }
}
