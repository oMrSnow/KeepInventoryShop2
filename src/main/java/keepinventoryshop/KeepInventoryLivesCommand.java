package keepinventoryshop;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
            case "lives":
                if (args.length == 1) {
                    showPlayerLives((CommandSender)player, player);
                } else if (args.length == 2 && sender.hasPermission("keepinventoryshop.view.others")) {
                    showPlayerLives(sender, Bukkit.getPlayer(args[1]));
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /keepinventory lives [<player>]");
                }
                return true;
            case "reload":
                if (sender.hasPermission("keepinventoryshop.reload")) {
                    this.plugin.reloadConfig(); // Reload the entire config.

                    // Load all the configuration values again from the newly reloaded config file.
                    int costPerLife = this.plugin.getConfig().getInt("cost-per-life", 500);
                    double costPerUpgrade = this.plugin.getConfig().getDouble("cost-per-upgrade", 1000);
                    int initialLives = this.plugin.getConfig().getInt("initial-lives");
                    int livesOnJoin = this.plugin.getConfig().getInt("lives-on-join");
                    boolean includeLivesOnJoin = this.plugin.getConfig().getBoolean("include-lives-on-join");
                    long joinTimer = this.plugin.getConfig().getLong("join-timer") * 1000L;
                    boolean useRegion = this.plugin.getConfig().getBoolean("use-region");

                    // Set all the configuration values again using the new values.
                    this.plugin.setCostPerLife(costPerLife);
                    this.plugin.setCostPerUpgrade(costPerUpgrade);
                    this.plugin.setInitialLives(initialLives);
                    this.plugin.setLivesOnJoin(livesOnJoin);
                    this.plugin.setIncludeLivesOnJoin(includeLivesOnJoin);
                    this.plugin.setJoinTimer(joinTimer);
                    this.plugin.setUseRegion(useRegion);

                    sender.sendMessage(ChatColor.GREEN + "KeepInventoryShop configuration reloaded.");
                } else {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                }
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown command. Type /keepinventory help for a list of commands.");
                return true;
        }
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

                // Remove the player from the deactivatedMessageSentPlayers set when they upgrade a life
                this.plugin.removeDeactivatedMessageSentPlayer(player.getUniqueId());
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
        UUID targetUUID = targetPlayer.getUniqueId();
        int remainingLives = this.plugin.getPlayerKeepInventoryMap().getOrDefault(targetUUID, 0);
        int remainingUpgradedLives = this.plugin.getPlayerUpgradedKeepInventoryMap().getOrDefault(targetUUID, 0);

        if (sender.getName().equals(targetPlayer.getName())) {
            if (remainingLives > 0) {
                sender.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + remainingLives + " KeepInventory " + ChatColor.WHITE + getLifeWordForm(remainingLives) + " remaining.");
            }
            if (remainingUpgradedLives > 0) {
                sender.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + remainingUpgradedLives + " KeepTotem " + ChatColor.WHITE + getLifeWordForm(remainingUpgradedLives) + " remaining.");
            }
        } else {
            if (remainingLives > 0) {
                sender.sendMessage(ChatColor.WHITE + targetPlayer.getName() + " has " + ChatColor.LIGHT_PURPLE + remainingLives + ChatColor.WHITE + " KeepInventory " + getLifeWordForm(remainingLives) + " remaining.");
            }
            if (remainingUpgradedLives > 0) {
                sender.sendMessage(ChatColor.WHITE + targetPlayer.getName() + " has " + ChatColor.LIGHT_PURPLE + remainingUpgradedLives + ChatColor.WHITE + " KeepTotem " + getLifeWordForm(remainingUpgradedLives) + " remaining.");
            }
        }
    }


    private void buyLives(Player player, String arg) {
        try {
            int livesToPurchase = Integer.parseInt(arg);
            if (livesToPurchase > 0) {
                double totalCost = (livesToPurchase * this.plugin.getCostPerLife());
                EconomyResponse response = this.plugin.getEconomy().withdrawPlayer((OfflinePlayer) player, totalCost);
                if (response.transactionSuccess()) {
                    int currentLives = this.plugin.getPlayerKeepInventoryMap().getOrDefault(player.getUniqueId(), 0);
                    this.plugin.getPlayerKeepInventoryMap().put(player.getUniqueId(), currentLives + livesToPurchase);
                    player.sendMessage(ChatColor.GREEN + "You have successfully purchased " + livesToPurchase + " " + getLifeWordForm(livesToPurchase) + " for " + this.plugin.getEconomy().format(totalCost) + ".");

                    // Remove the player from the deactivatedMessageSentPlayers set when they buy a life
                    this.plugin.removeDeactivatedMessageSentPlayer(player.getUniqueId());
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
