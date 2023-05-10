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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;

public class KeepInventoryShop extends JavaPlugin implements Listener {
    private static KeepInventoryShop instance;

    public static KeepInventoryShop getInstance() {
        return instance;
    }
    public boolean isProtocolLibInstalled() {
        Plugin plugin = getServer().getPluginManager().getPlugin("ProtocolLib");
        return plugin != null && plugin.isEnabled();
    }
    private Set<UUID> deactivatedMessageSentPlayers = new HashSet<>();
    public void removeDeactivatedMessageSentPlayer(UUID playerUUID) {
        deactivatedMessageSentPlayers.remove(playerUUID);
    }
    private double costPerUpgrade;

    public void setCostPerUpgrade(double costPerUpgrade) {
        this.costPerUpgrade = costPerUpgrade;
    }

    public Map<UUID, Integer> getPlayerUpgradedKeepInventoryMap() {
        return playerUpgradedKeepInventoryMap;
    }
    private FileConfiguration playerDataConfig;

    private File playerDataFile;

    private int costPerLife;
    private String resourcePackUrl;

    private long joinTimer;

    private boolean includeLivesOnJoin;

    private int livesOnJoin;

    private int initialLives;
    private boolean useRegion;
    public boolean isUseRegion() {
        return useRegion;
    }
    public void setUseRegion(boolean useRegion) {
        this.useRegion = useRegion;
    }
    private Set<UUID> noLivesPlayers = new HashSet<>();
    private Map<UUID, Integer> playerKeepInventoryMap = new HashMap<>();

    private Map<UUID, Long> lastLoginTimestamp;

    private final Map<UUID, Long> lastLivesReceivedTimestamp = new HashMap<>();
    private HashMap<UUID, Long> lastJoinTimestamp = new HashMap<>();

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
    public void setInitialLives(int initialLives) {
        this.initialLives = initialLives;
    }
    public void setLivesOnJoin(int livesOnJoin) {
        this.livesOnJoin = livesOnJoin;
    }

    public void setIncludeLivesOnJoin(boolean includeLivesOnJoin) {
        this.includeLivesOnJoin = includeLivesOnJoin;
    }

    public void setJoinTimer(long joinTimer) {
        this.joinTimer = joinTimer;
    }

    private void playTotemAnimation(ArmorStand armorStand) {
        armorStand.getWorld().playSound(armorStand.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
    }
    private Map<UUID, Integer> playerUpgradedKeepInventoryMap = new HashMap<>();

    public void loadPluginConfiguration() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        costPerLife = getConfig().getInt("cost-per-life", 500);
        costPerUpgrade = getConfig().getInt("cost-per-upgrade", 1000);
        useRegion = getConfig().getBoolean("use-region", false);
        initialLives = getConfig().getInt("initial-lives");
        livesOnJoin = getConfig().getInt("lives-on-join");
        includeLivesOnJoin = getConfig().getBoolean("include-lives-on-join");
        joinTimer = getConfig().getLong("join-timer") * 1000L;
    }

    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Could not find Vault! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        loadPluginConfiguration();
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
        startLastLoginUpdateTask();
        startPeriodicCheckTask();
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

        for (Map.Entry<UUID, Integer> entry : this.playerUpgradedKeepInventoryMap.entrySet()) {
            UUID playerUUID = entry.getKey();
            int remainingUpgradedLives = ((Integer)entry.getValue()).intValue();
            getPlayerDataConfig().set("players." + playerUUID.toString() + ".upgradedLives", Integer.valueOf(remainingUpgradedLives));
        }

        savePlayerDataConfig();
    }

    private void startLastLoginUpdateTask() {
        for (Player player : getServer().getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            this.lastLoginTimestamp.put(playerUUID, Long.valueOf(System.currentTimeMillis()));
        }
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
        int savedUpgradedLives = getPlayerDataConfig().getInt("players." + playerUUID.toString() + ".upgradedLives", -1);

        boolean isFirstJoin = !getPlayerDataConfig().contains("players." + playerUUID.toString() + ".hasJoinedBefore");
        getPlayerDataConfig().set("players." + playerUUID.toString() + ".hasJoinedBefore", true);
        savePlayerDataConfig();

        if (isFirstJoin) {
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(this.initialLives));
            getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", Integer.valueOf(this.initialLives));
            savePlayerDataConfig();
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.GREEN + " Activated!" + ChatColor.WHITE + " You have " + ChatColor.LIGHT_PURPLE + this.initialLives + ChatColor.WHITE + " Initial " + getLifeWordForm(this.initialLives) + "!");

            // Set the lastJoinTimestamp for the player
            this.lastJoinTimestamp.put(playerUUID, System.currentTimeMillis());
        } else {
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(savedLives));
            this.playerUpgradedKeepInventoryMap.put(playerUUID, savedUpgradedLives);

            long timeDifference = calculateTimeDifference(playerUUID);
            if (timeDifference >= this.joinTimer && this.includeLivesOnJoin) {
                giveLivesOnJoin(player);

                // Set the lastJoinTimestamp for the player
                this.lastJoinTimestamp.put(playerUUID, System.currentTimeMillis());
            } else {
                displayCurrentLives(player, false);
            }
        }
    }

    private void loadResourcePack(Player player) {

        String resourcePackURL = getConfig().getString("resourcePackURL");
        if (resourcePackURL != null && !resourcePackURL.isEmpty()) {
            if (!player.hasMetadata("ResourcePackLoaded")) {
                player.setResourcePack(resourcePackURL);
                player.setMetadata("ResourcePackLoaded", new FixedMetadataValue(this, true));
//                getServer().getScheduler().runTaskLater(this, () -> {
//                    player.setResourcePack(resourcePackURL);
//                    player.setMetadata("ResourcePackLoaded", new FixedMetadataValue(this, true));
//                }, 20L);
            }
        }
    }

    private long calculateTimeDifference(UUID playerUUID) {
        long lastJoin = this.lastJoinTimestamp.getOrDefault(playerUUID, Long.valueOf(0L));
        long now = System.currentTimeMillis();
        return now - lastJoin;
    }

    private void displayCurrentLives(Player player, boolean onJoin) {
        int savedLives = this.playerKeepInventoryMap.getOrDefault(player.getUniqueId(), 0);
        int savedUpgradedLives = this.playerUpgradedKeepInventoryMap.getOrDefault(player.getUniqueId(), 0);

        if (savedLives > 0 || savedUpgradedLives > 0) {
            if (onJoin) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.GREEN + " Activated!");
            }
            if (savedLives > 0) {
                player.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + savedLives + " KeepInventory " + ChatColor.WHITE + getLifeWordForm(savedLives) + ".");
            }
            if (savedUpgradedLives > 0) {
                player.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + savedUpgradedLives + " KeepTotem " + ChatColor.WHITE + getLifeWordForm(savedUpgradedLives) + ".");
            }
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated!" + ChatColor.WHITE + " You have 0 lives left.");
        }
    }
    private void addLivesAndNotify(Player player, long timeDifference) {
        UUID playerUUID = player.getUniqueId();
        if (timeDifference >= this.joinTimer && this.includeLivesOnJoin) {
            int currentLives = this.playerKeepInventoryMap.get(playerUUID);
            int newLives = currentLives + this.livesOnJoin;
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(newLives));
            getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", newLives);
            savePlayerDataConfig();

            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.WHITE + " Timer Has Ended");
            player.sendMessage(ChatColor.WHITE + "You received " + ChatColor.LIGHT_PURPLE + this.livesOnJoin + " " + ChatColor.WHITE + getLifeWordForm(this.livesOnJoin) + ". You now have " + ChatColor.LIGHT_PURPLE + newLives + ChatColor.WHITE + " " + getLifeWordForm(newLives) + ".");

            deactivatedMessageSentPlayers.remove(playerUUID);
        } else {
            displayCurrentLives(player, false);
        }
    }

    private void startPeriodicCheckTask() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    UUID playerUUID = player.getUniqueId();
                    long timeDifference = calculateTimeDifference(playerUUID);
                    if (timeDifference >= joinTimer && includeLivesOnJoin) {
                        addLivesAndNotify(player, timeDifference);
                        lastJoinTimestamp.put(playerUUID, System.currentTimeMillis());
                    }
                }
            }
        }, 0L, 20L * 60L); // Check every minute
    }
    private long getOnlineTime(Player player) {
        UUID playerUUID = player.getUniqueId();
        long lastLogin = this.lastLoginTimestamp.getOrDefault(playerUUID, Long.valueOf(System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        return now - lastLogin;
    }

    private void giveLivesOnJoin(Player player) {
        UUID playerUUID = player.getUniqueId();
        long lastLogin = player.getLastPlayed();
        long now = System.currentTimeMillis();
        long lastQuit = this.lastQuitTimestamp.getOrDefault(playerUUID, Long.valueOf(0L));
        long timeDifference = now - Math.max(lastLogin, lastQuit);
        long timeOnline = getOnlineTime(player);
        timeDifference += timeOnline;
        addLivesAndNotify(player, timeDifference);
    }



    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();
        int remainingLives = this.playerKeepInventoryMap.getOrDefault(playerUUID, 0);
        boolean isInRegion = true;
        if (isUseRegion()) {
            isInRegion = isPlayerInRegion(player, "keep_inventory_zone");
        }

        if (remainingLives > 0 || isInRegion) {
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
                final int finalRemainingLives = remainingLives - 1;
                remainingLives -= 1;
                Bukkit.getScheduler().runTaskLater(this, () -> player.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + finalRemainingLives + ChatColor.WHITE + " " + getLifeWordForm(finalRemainingLives) + " left."), 5L);
            } else {
                if (!noLivesPlayers.contains(playerUUID)) {
                    noLivesPlayers.add(playerUUID);
                    if (!deactivatedMessageSentPlayers.contains(playerUUID)) {
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (!deactivatedMessageSentPlayers.contains(playerUUID)) {
                                player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated.");
                                deactivatedMessageSentPlayers.add(playerUUID);
                            }
                        }, 5L);
                    }
                }
                remainingLives = 0;
            }
            this.playerKeepInventoryMap.put(playerUUID, remainingLives);
            getPlayerDataConfig().set(playerUUID + ".lives", remainingLives);
            savePlayerDataConfig();
        } else {
            int remainingUpgradedLives = this.playerUpgradedKeepInventoryMap.getOrDefault(playerUUID, 0);
            if (remainingUpgradedLives == 0 && !deactivatedMessageSentPlayers.contains(playerUUID)) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!deactivatedMessageSentPlayers.contains(playerUUID)) {
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated.");
                        deactivatedMessageSentPlayers.add(playerUUID);
                    }
                }, 30L);
            }
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        }

        if (!player.hasMetadata("keepInventoryWarningShown") && player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY).booleanValue()) {
            player.setMetadata("keepInventoryWarningShown", new FixedMetadataValue(this, true));
        }
    }


    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUniqueId();
            int remainingUpgradedLives = this.playerUpgradedKeepInventoryMap.getOrDefault(playerUUID, 0);

            if (remainingUpgradedLives > 0 && player.getHealth() - event.getFinalDamage() <= 0) {
                event.setDamage(0); // Cancel the damage
                consumeUpgradedLife(player, remainingUpgradedLives);
            }
        }
    }

    public void consumeUpgradedLife(Player player, int remainingUpgradedLives) {
        UUID playerUUID = player.getUniqueId();

        // Heal the player and clear any existing potion effects
        player.setHealth(player.getMaxHealth());
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // Apply enchanted golden apple effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 3)); // 10 seconds, level 4
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 3)); // 5 seconds, level 4
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0)); // 5 seconds, level 1
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0)); // 10 seconds, level 1

        if (isProtocolLibInstalled()) {
            // Play custom KeepTotem animation using ProtocolLib
            CustomSwirlingTask swirlingTask = new CustomSwirlingTask(player);
            int taskID = swirlingTask.runTaskTimer(this, 0L, 1L).getTaskId();
            swirlingTask.setTaskID(taskID);
        } else {
            // Play  particles and sound effect
            double spread = 0.5D;
            player.getWorld().spawnParticle(Particle.SPELL_INSTANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 200, spread, spread, spread, 0.0D);
            player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0.0D, 1.0D, 0.0D), 200, spread, spread, spread, 0.0D, new Particle.DustOptions(Color.PURPLE, 1.0F));
            player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0.0D, 1.0D, 0.0D), 200, spread, spread, spread, 0.0D, new Particle.DustOptions(Color.WHITE, 1.0F));
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
        }

        // Update the remaining upgraded lives
        if (remainingUpgradedLives > 1) {
            this.playerUpgradedKeepInventoryMap.put(playerUUID, remainingUpgradedLives - 1);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepTotem" + ChatColor.WHITE + " saved you! You have " + ChatColor.LIGHT_PURPLE + (remainingUpgradedLives - 1) + " KeepTotem " + ChatColor.WHITE + getLifeWordForm(remainingUpgradedLives - 1) + " Remaining.");
        } else {
            this.playerUpgradedKeepInventoryMap.remove(playerUUID);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepTotem" + ChatColor.RED + " Deactivated");

            // Add the "KeepInventory Deactivated" message if no regular lives left
            int remainingRegularLives = this.playerKeepInventoryMap.getOrDefault(playerUUID, 0);
            if (remainingRegularLives == 0) {
                noLivesPlayers.add(playerUUID);
            }

            deactivatedMessageSentPlayers.remove(playerUUID); // Remove the player from the deactivatedMessageSentPlayers set
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        int currentLives = playerKeepInventoryMap.getOrDefault(playerUUID, 0);
        int currentUpgradedLives = playerUpgradedKeepInventoryMap.getOrDefault(playerUUID, 0);

        savePlayerLives(playerUUID, currentLives);
        savePlayerUpgradedLives(playerUUID, currentUpgradedLives);

        this.lastQuitTimestamp.put(playerUUID, System.currentTimeMillis());
        if (player.hasMetadata("ResourcePackLoaded")) {
            player.removeMetadata("ResourcePackLoaded", this);
        }
    }

    public void savePlayerLives(UUID playerUUID, int lives) {
        getPlayerDataConfig().set("players." + playerUUID.toString() + ".lives", lives);
        savePlayerDataConfig();
    }

    public void savePlayerUpgradedLives(UUID playerUUID, int upgradedLives) {
        getPlayerDataConfig().set("players." + playerUUID.toString() + ".upgradedLives", upgradedLives);
        savePlayerDataConfig();
    }

    private String getLifeWordForm(int count) {
        return (count == 1) ? "life" : "lives";
    }

    public void updatePlayerLivesOnJoin(Player player) {}
}
