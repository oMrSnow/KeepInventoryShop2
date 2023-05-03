package keepinventoryshop;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

public class KeepInventoryLivesCommand implements CommandExecutor, TabCompleter {

    private KeepInventoryShop plugin;

    private int costPerLife;

    public KeepInventoryLivesCommand(KeepInventoryShop plugin) {
        this.plugin = plugin;
    }
    public int getCostPerLife() {
        return this.costPerLife;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return ImmutableList.of();
        }

        if (args.length == 1) {
            commands.add("buy");
            commands.add("upgrade");
            commands.add("set");
            commands.add("add");
            commands.add("remove");
            commands.add("help");
            commands.add("reload");
            commands.add("lives");
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("lives"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                commands.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[1], commands, completions);
        }

        Collections.sort(completions);
        return completions;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) {
            showPlayerLives((CommandSender)player, player);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "buy":
                if (args.length == 2) {
                    buyLives(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory buy <amount>");
                }
                return true;
            case "upgrade":
                if (args.length == 1 || args.length == 2) {
                    int livesToUpgrade = (args.length == 2) ? Integer.parseInt(args[1]) : 1;
                    upgradeLives(player, livesToUpgrade);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory upgrade [<amount>]");
                }
                return true;
            case "set":
                if (args.length == 3 && sender.hasPermission("keepinventoryshop.set")) {
                    setLives(sender, args[1], args[2]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory set <player> <amount>");
                }
                return true;
            case "add":
                if (args.length == 3 && sender.hasPermission("keepinventoryshop.add")) {
                    addLives(sender, args[1], args[2]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory add <player> <amount>");
                }
                return true;
            case "remove":
                if (args.length == 3 && sender.hasPermission("keepinventoryshop.remove")) {
                    removeLives(sender, args[1], args[2]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory remove <player> <amount>");
                }
                return true;
            case "help":
                displayHelpMessage(player);
                return true;
            case "reload":
                if (sender.hasPermission("keepinventoryshop.reload")) {
                    this.plugin.reloadPluginConfiguration();
                    int costPerLife = this.plugin.getConfig().getInt("cost-per-life", 500);
                    this.plugin.setCostPerLife(costPerLife);
                    sender.sendMessage(ChatColor.GREEN + "KeepInventoryShop configuration reloaded.");
                } else {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                }
                return true;
            case "lives":
                if (args.length == 1) {
                    showPlayerLives((CommandSender)player, player);
                } else if (args.length == 2 && sender.hasPermission("keepinventoryshop.view.others")) {
                    showPlayerLives(sender, Bukkit.getPlayer(args[1]));
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory lives [<player>]");
                }
                return true;
        }
        player.sendMessage(ChatColor.RED + "Unknown command. Type /keepinventory help for a list of commands.");
        return true;
    }

    private void displayHelpMessage(Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "KeepInventoryShop Commands:");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory" + ChatColor.WHITE + " - Shows your lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory lives" + ChatColor.WHITE + " - Show the lives of online players.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory buy <amount>" + ChatColor.WHITE + " - Buy specified amount of lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory upgrade <amount>" + ChatColor.WHITE + " - Upgrades your lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory set <player> <amount>" + ChatColor.WHITE + " - Sets lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory add <player> <amount>" + ChatColor.WHITE + " - Add lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory remove <player> <amount>" + ChatColor.WHITE + " - Remove lives.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory help" + ChatColor.WHITE + " - Show help for Keep Inventory commands.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/keepinventory reload" + ChatColor.WHITE + " - Reload config files.");
    }
    private void upgradeLives(Player player, int livesToUpgrade) {
        int currentLives = this.plugin.getPlayerKeepInventoryMap().getOrDefault(player.getUniqueId(), 0);
        if (currentLives >= livesToUpgrade) {
            double totalCost = (livesToUpgrade * this.plugin.getCostPerUpgrade());
            EconomyResponse response = this.plugin.getEconomy().withdrawPlayer((OfflinePlayer) player, totalCost);
            if (response.transactionSuccess()) {
                this.plugin.getPlayerKeepInventoryMap().put(player.getUniqueId(), currentLives - livesToUpgrade);
                this.plugin.getPlayerUpgradedKeepInventoryMap().put(player.getUniqueId(), this.plugin.getPlayerUpgradedKeepInventoryMap().getOrDefault(player.getUniqueId(), 0) + livesToUpgrade);
                player.sendMessage(ChatColor.GREEN + "You have successfully upgraded " + livesToUpgrade + " " + getLifeWordForm(livesToUpgrade) + " for " + this.plugin.getEconomy().format(totalCost) + ".");
            } else {
                player.sendMessage(ChatColor.RED + "You do not have enough money to upgrade " + livesToUpgrade + " " + getLifeWordForm(livesToUpgrade) + ".");
            }
        } else {
            player.sendMessage(ChatColor.RED + "You do not have enough KeepInventory lives to upgrade.");
        }
    }

    private void showPlayerLives(CommandSender sender, Player targetPlayer) {
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        int remainingLives = ((Integer)this.plugin.getPlayerKeepInventoryMap().getOrDefault(targetPlayer.getUniqueId(), Integer.valueOf(0))).intValue();
        if (remainingLives == 0) {
            sender.sendMessage(ChatColor.WHITE + targetPlayer.getName() + " does not have any " + ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.WHITE + " lives.");
        } else {
            sender.sendMessage(ChatColor.WHITE + targetPlayer.getName() + " has " + ChatColor.LIGHT_PURPLE + remainingLives + " " + getLifeWordForm(remainingLives) + ChatColor.WHITE + " left.");
        }
    }

    private void buyLives(Player player, String arg) {
        try {
            int livesToPurchase = Integer.parseInt(arg);
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
    }

    private void setLives(CommandSender sender, String playerName, String amount) {
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        try {
            int lives = Integer.parseInt(amount);
            if (lives >= 0) {
                this.plugin.getPlayerKeepInventoryMap().put(targetPlayer.getUniqueId(), Integer.valueOf(lives));
                sender.sendMessage(ChatColor.GREEN + "Set " + targetPlayer.getName() + "'s lives to " + lives + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Please enter a non-negative number of lives.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid input. Please enter a valid number of lives.");
        }
    }

    private void addLives(CommandSender sender, String playerName, String amount) {
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        try {
            int livesToAdd = Integer.parseInt(amount);
            if (livesToAdd > 0) {
                int currentLives = ((Integer)this.plugin.getPlayerKeepInventoryMap().getOrDefault(targetPlayer.getUniqueId(), Integer.valueOf(0))).intValue();
                this.plugin.getPlayerKeepInventoryMap().put(targetPlayer.getUniqueId(), Integer.valueOf(currentLives + livesToAdd));
                sender.sendMessage(ChatColor.GREEN + "Added " + livesToAdd + " lives to " + targetPlayer.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Please enter a positive number of lives to add.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid input. Please enter a valid number of lives to add.");
        }
    }

    private void removeLives(CommandSender sender, String playerName, String amount) {
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        try {
            int livesToRemove = Integer.parseInt(amount);
            if (livesToRemove > 0) {
                int currentLives = ((Integer)this.plugin.getPlayerKeepInventoryMap().getOrDefault(targetPlayer.getUniqueId(), Integer.valueOf(0))).intValue();
                int newLives = Math.max(0, currentLives - livesToRemove);
                this.plugin.getPlayerKeepInventoryMap().put(targetPlayer.getUniqueId(), Integer.valueOf(newLives));
                sender.sendMessage(ChatColor.GREEN + "Removed " + livesToRemove + " lives from " + targetPlayer.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Please enter a positive number of lives to remove.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid input. Please enter a valid number of lives to remove.");
        }
    }

    private String getLifeWordForm(int count) {
        return (count == 1) ? "life" : "lives";
    }
}
