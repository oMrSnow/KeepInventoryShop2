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
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("keepinventoryshop.reload")) {
                this.plugin.reloadPluginConfiguration();
                int costPerLife = this.plugin.getConfig().getInt("cost-per-life", 500);
                double costPerUpgrade = this.plugin.getConfig().getDouble("cost-per-upgrade", 1000);
                int initialLives = this.plugin.getConfig().getInt("initial-lives");
                int livesOnJoin = this.plugin.getConfig().getInt("lives-on-join");
                boolean includeLivesOnJoin = this.plugin.getConfig().getBoolean("include-lives-on-join");
                long joinTimer = this.plugin.getConfig().getLong("join-timer") * 1000L;

                this.plugin.setCostPerLife(costPerLife);
                this.plugin.setCostPerUpgrade(costPerUpgrade);
                this.plugin.setInitialLives(initialLives);
                this.plugin.setLivesOnJoin(livesOnJoin);
                this.plugin.setIncludeLivesOnJoin(includeLivesOnJoin);
                this.plugin.setJoinTimer(joinTimer);

                sender.sendMessage(ChatColor.GREEN + "KeepInventoryShop configuration reloaded.");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        }
        return false;
    }
}
