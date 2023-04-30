package keepinventoryshop;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KeepInventoryLivesCommand implements CommandExecutor {
    private KeepInventoryShop plugin;

    private int costPerLife;

    public KeepInventoryLivesCommand(KeepInventoryShop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) {
            int remainingLives = ((Integer)this.plugin.getPlayerKeepInventoryMap().getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
            if (remainingLives == 0) {
                player.sendMessage(ChatColor.WHITE + "You do not have any " + ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.WHITE + " lives.");
            } else {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.WHITE + " is activated. You have " + ChatColor.LIGHT_PURPLE + remainingLives + " " + getLifeWordForm(remainingLives) + ChatColor.WHITE + " left.");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            displayHelpMessage(player);
            return true;
        }
        if (args.length == 1) {
            try {
                int livesToPurchase = Integer.parseInt(args[0]);
                if (livesToPurchase > 0) {
                    double totalCost = (livesToPurchase * this.plugin.getCostPerLife());
                    EconomyResponse response = this.plugin.getEconomy().withdrawPlayer((OfflinePlayer)player, totalCost);
                    if (response.transactionSuccess()) {
                        int currentLives = ((Integer)this.plugin.getPlayerKeepInventoryMap().getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
                        this.plugin.getPlayerKeepInventoryMap().put(player.getUniqueId(), Integer.valueOf(currentLives + livesToPurchase));
                        player.sendMessage(ChatColor.GREEN + "You have successfully purchased " + livesToPurchase + " " + getLifeWordForm(livesToPurchase) + " for " + this.plugin.getEconomy().format(totalCost) + ".");
                    } else {
                        player.sendMessage(ChatColor.RED + "You do not have enough money to purchase " + livesToPurchase + " " + getLifeWordForm(livesToPurchase) + ".");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Please enter a positive number of lives to purchase.");
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid input. Please enter a valid number of lives to purchase.");
            }
            return true;
        }
        player.sendMessage(ChatColor.RED + "Invalid command. Type '/keepinventory help' for help.");
        return true;
    }

    private void displayHelpMessage(Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "KeepInventoryShop Commands:");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory [number of lives]" + ChatColor.WHITE + " - Purchase Keep Inventory lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory help" + ChatColor.WHITE + " - Show this help message.");
    }

    private String getLifeWordForm(int count) {
        return (count == 1) ? "life" : "lives";
    }
}
