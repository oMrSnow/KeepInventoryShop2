package keepinventoryshop;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.io.File;
import java.io.IOException;
import java.util.*;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class KeepInventoryShop extends JavaPlugin implements Listener {
    private Set<UUID> deactivatedMessageSentPlayers = new HashSet<>();

    public Map<UUID, Integer> getPlayerUpgradedKeepInventoryMap() {
        return playerUpgradedKeepInventoryMap;
    }
    private FileConfiguration playerDataConfig;

    private File playerDataFile;

    private int costPerLife;

    private long joinTimer;

    private boolean includeLivesOnJoin;

    private int livesOnJoin;

    private int initialLives;
    private boolean useRegion;
    public boolean isUseRegion() {
        return useRegion;
    }
    private Set<UUID> noLivesPlayers = new HashSet<>();
    private Map<UUID, Integer> playerKeepInventoryMap = new HashMap<>();

    private Map<UUID, Long> lastLoginTimestamp;

    private final Map<UUID, Long> lastLivesReceivedTimestamp = new HashMap<>();

    private Map<UUID, Long> lastQuitTimestamp;

    private Economy economy;

    public HashMap<Object, Object> inventoryExpiredMessageShown;

    public void setCostPerLife(int costPerLife) {
        this.costPerLife = costPerLife;
    }

    public int getCostPerLife() {
        return this.costPerLife;
    }
    //Upgraded life
    private double costPerUpgrade;

    public void loadPluginConfiguration() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        costPerLife = getConfig().getInt("cost-per-life", 500);
        costPerUpgrade = getConfig().getInt("cost-per-upgrade", 1000);
        useRegion = getConfig().getBoolean("use-region", false);
    }
    private Map<UUID, Integer> playerUpgradedKeepInventoryMap = new HashMap<>();

    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Could not find Vault! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
            Bukkit.getPluginManager().registerEvents(this, (Plugin)this);
            getServer().getPluginManager().registerEvents(this, this);
            return;
        }
        saveDefaultConfig();
        this.playerKeepInventoryMap = new HashMap<>();
        this.initialLives = getConfig().getInt("initial-lives");
        this.livesOnJoin = getConfig().getInt("lives-on-join");
        this.includeLivesOnJoin = getConfig().getBoolean("include-lives-on-join");
        this.joinTimer = getConfig().getLong("join-timer") * 1000L;
        this.costPerLife = getConfig().getInt("cost-per-life", 500);
        costPerUpgrade = getConfig().getDouble("cost-per-upgrade");
        loadPlayerDataConfig();
        Bukkit.getPluginManager().registerEvents(this, (Plugin)this);
        int costPerLife = getConfig().getInt("cost-per-life", 500);
        this.lastLoginTimestamp = new HashMap<>();
        this.lastQuitTimestamp = new HashMap<>();
        KeepInventoryLivesCommand keepInventoryLivesCommand = new KeepInventoryLivesCommand(this);
        getCommand("keepinventory").setExecutor(new KeepInventoryLivesCommand(this));
        getCommand("keepinventory").setTabCompleter(new KeepInventoryLivesCommand(this));
        deactivatedMessageSentPlayers = new HashSet<>();
    }
    public double getCostPerUpgrade() {
        return costPerUpgrade;
    }

    public void onDisable() {
        saveConfig();
        saveAllPlayerData();
    }

    private void saveAllPlayerData() {
        for (Map.Entry<UUID, Integer> entry : this.playerKeepInventoryMap.entrySet()) {
            UUID playerUUID = entry.getKey();
            int remainingLives = ((Integer)entry.getValue()).intValue();
            getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", Integer.valueOf(remainingLives));
        }
        savePlayerDataConfig();
    }

    private void startLastLoginUpdateTask() {
        for (Player player : getServer().getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            this.lastLoginTimestamp.put(playerUUID, Long.valueOf(System.currentTimeMillis()));
        }
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        this.initialLives = getConfig().getInt("initial-lives");
        this.livesOnJoin = getConfig().getInt("lives-on-join");
        this.includeLivesOnJoin = getConfig().getBoolean("include-lives-on-join");
        this.joinTimer = getConfig().getLong("join-timer") * 1000L;
        useRegion = getConfig().getBoolean("use-region", false);
    }

    private void loadPlayerDataConfig() {
        this.playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!this.playerDataFile.exists())
            saveResource("playerdata.yml", false);
        this.playerDataConfig = (FileConfiguration)YamlConfiguration.loadConfiguration(this.playerDataFile);
    }

    public FileConfiguration getPlayerDataConfig() {
        if (this.playerDataConfig == null)
            loadPlayerDataConfig();
        return this.playerDataConfig;
    }

    public void savePlayerDataConfig() {
        try {
            getPlayerDataConfig().save(this.playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save player data config.");
            e.printStackTrace();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null)
            return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
            return false;
        this.economy = (Economy)rsp.getProvider();
        return (this.economy != null);
    }

    private boolean isPlayerInRegion(Player player, String regionId) {
        if (isWorldGuardAvailable()) {
            Location location = player.getLocation();
            WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
            World worldEditWorld = BukkitAdapter.adapt(location.getWorld());
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(worldEditWorld);
            if (regionManager != null) {
                ApplicableRegionSet applicableRegions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
                for (ProtectedRegion region : applicableRegions) {
                    if (region.getId().equalsIgnoreCase(regionId))
                        return true;
                }
            }
        } else {
            return false;
        }
        return false;
    }

    private boolean isWorldGuardAvailable() {
        Plugin worldGuardPlugin = getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuardPlugin != null && worldGuardPlugin instanceof WorldGuardPlugin)
            return true;
        return false;
    }

    public Economy getEconomy() {
        return this.economy;
    }

    public Map<UUID, Integer> getPlayerKeepInventoryMap() {
        return this.playerKeepInventoryMap;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        int savedLives = getPlayerDataConfig().getInt("players." + playerUUID.toString() + ".lives", -1);

        if (!playerKeepInventoryMap.containsKey(playerUUID) && savedLives == -1) {
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(this.initialLives));
            getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", Integer.valueOf(this.initialLives));
            savePlayerDataConfig();
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.GREEN + " Activated!" + ChatColor.WHITE + " You have " + ChatColor.LIGHT_PURPLE + this.initialLives + ChatColor.WHITE + " Initial " + getLifeWordForm(this.initialLives) + "!");
        } else {
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(savedLives));
        }
        long timeDifference = calculateTimeDifference(playerUUID);
        if (timeDifference >= this.joinTimer) {
            giveLivesOnJoin(player);
            this.lastLivesReceivedTimestamp.put(playerUUID, Long.valueOf(System.currentTimeMillis()));
            deactivatedMessageSentPlayers.remove(playerUUID); // Remove the player from the deactivatedMessageSentPlayers set
        } else {
            displayCurrentLives(player);
        }
    }

    private long calculateTimeDifference(UUID playerUUID) {
        long lastLivesReceived = ((Long)this.lastLivesReceivedTimestamp.getOrDefault(playerUUID, Long.valueOf(0L))).longValue();
        long now = System.currentTimeMillis();
        long timeDifference = now - lastLivesReceived;
        return timeDifference;
    }

    private void displayCurrentLives(Player player) {
        int savedLives = ((Integer)this.playerKeepInventoryMap.getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
        if (savedLives > 0) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.GREEN + " Activated!" + ChatColor.WHITE + " You have " + ChatColor.LIGHT_PURPLE + savedLives + " " + ChatColor.WHITE + getLifeWordForm(savedLives) + "!");
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated!" + ChatColor.WHITE + " You have 0 lives left.");
        }
    }

    private void giveLivesOnJoin(Player player) {
        UUID playerUUID = player.getUniqueId();
        long lastLogin = player.getLastPlayed();
        long now = System.currentTimeMillis();
        long lastQuit = ((Long)this.lastQuitTimestamp.getOrDefault(playerUUID, Long.valueOf(0L))).longValue();
        long timeDifference = now - Math.max(lastLogin, lastQuit);
        long timeOnline = now - ((Long)this.lastLoginTimestamp.getOrDefault(playerUUID, Long.valueOf(now))).longValue();
        timeDifference += timeOnline;
        if (timeDifference >= this.joinTimer) {
            int currentLives = ((Integer)this.playerKeepInventoryMap.get(playerUUID)).intValue();
            int newLives = currentLives + this.livesOnJoin;
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(newLives));
            getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", newLives);
            savePlayerDataConfig();
            player.sendMessage(ChatColor.WHITE + "You received " + ChatColor.LIGHT_PURPLE + this.livesOnJoin + " KeepInventory " + ChatColor.WHITE + getLifeWordForm(this.livesOnJoin) + ". You now have " + ChatColor.LIGHT_PURPLE + newLives + ChatColor.WHITE + " " + getLifeWordForm(newLives) + ".");
            deactivatedMessageSentPlayers.remove(playerUUID); // Remove the player from the deactivatedMessageSentPlayers set
        } else {
            displayCurrentLives(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();
        int remainingLives = this.playerKeepInventoryMap.getOrDefault(playerUUID, Integer.valueOf(0)).intValue();
        boolean isInRegion = true;
        if (isUseRegion()) {
            isInRegion = isPlayerInRegion(player, "keep_inventory_zone");
        }

        if (remainingLives > 0 || isInRegion) {
            // Check if the player is in the noLivesPlayers set
            if (noLivesPlayers.contains(playerUUID)) {
                event.setKeepInventory(false);
                event.setKeepLevel(false);
                noLivesPlayers.remove(playerUUID);
            } else {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.setDroppedExp(0);
                event.getDrops().clear();
            }

            if (remainingLives > 1) {
                remainingLives -= 1;
            } else {
                remainingLives = 0;
                noLivesPlayers.add(playerUUID);
            }
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(remainingLives));
            getPlayerDataConfig().set(playerUUID + ".lives", Integer.valueOf(remainingLives));
            savePlayerDataConfig();

            final int finalRemainingLives = remainingLives;
            Bukkit.getScheduler().runTaskLater((Plugin) this, () -> {
                if (finalRemainingLives == 0 && !deactivatedMessageSentPlayers.contains(playerUUID)) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated.");
                    deactivatedMessageSentPlayers.add(playerUUID);
                } else if (finalRemainingLives > 0) {
                    player.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + finalRemainingLives + ChatColor.WHITE + " " + getLifeWordForm(finalRemainingLives) + " left.");
                }
            }, 30L);
        } else {
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        }

        if (!player.hasMetadata("keepInventoryWarningShown") && player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY).booleanValue()) {
            player.setMetadata("keepInventoryWarningShown", new FixedMetadataValue(this, Boolean.valueOf(true)));
        }
    }


    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUniqueId();
            int remainingUpgradedLives = this.playerUpgradedKeepInventoryMap.getOrDefault(playerUUID, 0);

            if (remainingUpgradedLives > 0 && player.getHealth() - event.getFinalDamage() <= 0) {
                event.setCancelled(true);

                // Apply enchanted golden apple effects
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 4)); // 5 seconds, level 5
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 3)); // 5 seconds, level 4
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0)); // 5 seconds, level 1
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0)); // 10 seconds, level 1

                // Particle effect
                double spread = 0.5;
                player.getWorld().spawnParticle(Particle.SPELL_INSTANT, player.getLocation().add(0, 1, 0), 200, spread, spread, spread, 0);
                player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 200, spread, spread, spread, 0, new Particle.DustOptions(Color.PURPLE, 1.0F));
                player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 200, spread, spread, spread, 0, new Particle.DustOptions(Color.WHITE, 1.0F));

                // Sound effect
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);

                // Update the remaining upgraded lives
                if (remainingUpgradedLives > 1) {
                    this.playerUpgradedKeepInventoryMap.put(playerUUID, remainingUpgradedLives - 1);
                    player.sendMessage(ChatColor.GREEN + "Your upgraded KeepInventory life saved you! You have " + (remainingUpgradedLives - 1) + " upgraded " + getLifeWordForm(remainingUpgradedLives - 1) + " remaining.");
                } else {
                    this.playerUpgradedKeepInventoryMap.remove(playerUUID);
                    player.sendMessage(ChatColor.RED + "You have run out of Upgraded lives.");

                    // Check if the player is out of normal KeepInventory lives
                    int remainingLives = this.playerKeepInventoryMap.getOrDefault(playerUUID, 0);
                    if (remainingLives == 0) {
                        noLivesPlayers.add(playerUUID);
                        if (!deactivatedMessageSentPlayers.contains(playerUUID)) {
                            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated.");
                            deactivatedMessageSentPlayers.add(playerUUID);
                        }
                    } else {
                        deactivatedMessageSentPlayers.remove(playerUUID);
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Upgraded " + getLifeWordForm(remainingUpgradedLives - 1) + " Deactivated, " + ChatColor.WHITE + remainingLives + " KI " + getLifeWordForm(remainingLives) + " Remaining.");
                    }
                }
            }
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        int currentLives = ((Integer)this.playerKeepInventoryMap.get(playerUUID)).intValue();
        getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", Integer.valueOf(currentLives));
        savePlayerDataConfig();
        this.lastQuitTimestamp.put(playerUUID, Long.valueOf(System.currentTimeMillis()));
    }

    public void savePlayerLives(UUID playerUUID, int lives) {
        getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", Integer.valueOf(lives));
        savePlayerDataConfig();
    }

    private String getLifeWordForm(int count) {
        return (count == 1) ? "life" : "lives";
    }

    public void updatePlayerLivesOnJoin(Player player) {}
}
