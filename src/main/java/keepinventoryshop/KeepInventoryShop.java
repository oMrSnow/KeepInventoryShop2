package keepinventoryshop;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.session.SessionManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KeepInventoryShop extends JavaPlugin implements Listener {

    // Configuration and data files
    private FileConfiguration playerDataConfig;
    private File playerDataFile;

    // Plugin settings
    private int costPerLife;
    private long joinTimer;
    private boolean includeLivesOnJoin;
    private int livesOnJoin;
    private int initialLives;

    // Data storage
    private Map<UUID, Integer> playerKeepInventoryMap = new HashMap<>();
    private Map<UUID, Long> lastLoginTimestamp;
    private final Map<UUID, Long> lastLivesReceivedTimestamp = new HashMap<>();
    private Map<UUID, Long> lastQuitTimestamp;

    // Economy integration
    private Economy economy;
    public HashMap<Object, Object> inventoryExpiredMessageShown;

    // Custom WorldGuard flag
    public static final StateFlag MY_CUSTOM_FLAG = new StateFlag("keep-inventory-shop", false);

    // Setters and getters for the cost per life
    public void setCostPerLife(int costPerLife) {
        this.costPerLife = costPerLife;
    }

    public int getCostPerLife() {
        return this.costPerLife;
    }
    private void registerCustomHandler() {
        WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
        SessionManager sessionManager = worldGuard.getSessionManager();
        sessionManager.registerHandlerFactory(KeepInventoryShop.MY_CUSTOM_FLAG, new MyCustomHandler.Factory(this));
    }
    private void registerEventListener() {
        // Create an instance of your event listener class and register it
        MyCustomEventListener eventListener = new MyCustomEventListener(this);
        Bukkit.getPluginManager().registerEvents(eventListener, this);
    }
    // Plugin lifecycle events
    @Override
    public void onLoad() {
        if (isWorldGuardEnabled()) {
            registerCustomFlags();
        }
    }
    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Could not find Vault! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (isWorldGuardAvailable()) {
            registerCustomFlags();
            registerCustomHandler();
            registerEventListener();
        } else {
            getLogger().warning("WorldGuard not found. KeepInventoryShop's features will be disabled.");
        }
        saveDefaultConfig();
        this.playerKeepInventoryMap = new HashMap<>();
        this.initialLives = getConfig().getInt("initial-lives");
        this.livesOnJoin = getConfig().getInt("lives-on-join");
        this.includeLivesOnJoin = getConfig().getBoolean("include-lives-on-join");
        this.joinTimer = getConfig().getLong("join-timer") * 1000L;
        this.costPerLife = getConfig().getInt("cost-per-life", 500);
        loadPlayerDataConfig();
        int costPerLife = getConfig().getInt("cost-per-life", 500);
        this.lastLoginTimestamp = new HashMap<>();
        this.lastQuitTimestamp = new HashMap<>();
        getCommand("keepinventory").setExecutor(new KeepInventoryLivesCommand(this));
        getCommand("keepinventoryreload").setExecutor(new KeepInventoryReloadCommand(this));
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    public boolean isWorldGuardAvailable() {
        return getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    private boolean isWorldGuardEnabled() {
        Plugin worldGuardPlugin = getServer().getPluginManager().getPlugin("WorldGuard");
        return worldGuardPlugin != null && worldGuardPlugin instanceof WorldGuardPlugin;
    }

    private void registerCustomFlags() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            KeepInventoryFlag flag = new KeepInventoryFlag("keep-inventory-shop", false, RegionGroup.ALL);
            registry.register(flag);
        } catch (FlagConflictException e) {
            getLogger().warning("Failed to register custom flag:" + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        saveConfig();
        saveAllPlayerData();
    }

    private void saveAllPlayerData() {
        for (Map.Entry<UUID, Integer> entry : this.playerKeepInventoryMap.entrySet()) {
            UUID playerUUID = entry.getKey();
            int remainingLives = entry.getValue().intValue();
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
    }

    private void loadPlayerDataConfig() {
        this.playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!this.playerDataFile.exists())
            saveResource("playerdata.yml", false);
        this.playerDataConfig = YamlConfiguration.loadConfiguration(this.playerDataFile);
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
        this.economy = rsp.getProvider();
        return (this.economy != null);
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
        int savedLives = getPlayerDataConfig().getInt("players." + playerUUID + ".lives", -1);
        if (savedLives == -1) {
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(this.initialLives));
            getPlayerDataConfig().set("players." + playerUUID + ".lives", Integer.valueOf(this.initialLives));
            savePlayerDataConfig();
            player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.GREEN + " Activated!" + ChatColor.WHITE + " You have " + ChatColor.LIGHT_PURPLE + this.initialLives + ChatColor.WHITE + " Initial " + getLifeWordForm(this.initialLives) + "!");
        } else {
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(savedLives));
        }
        long timeDifference = calculateTimeDifference(playerUUID);
        if (timeDifference >= this.joinTimer) {
            giveLivesOnJoin(player);
            this.lastLivesReceivedTimestamp.put(playerUUID, Long.valueOf(System.currentTimeMillis()));
        } else {
            displayCurrentLives(player);
        }
    }

    private long calculateTimeDifference(UUID playerUUID) {
        long lastLivesReceived = this.lastLivesReceivedTimestamp.getOrDefault(playerUUID, Long.valueOf(0L)).longValue();
        long now = System.currentTimeMillis();
        long timeDifference = now - lastLivesReceived;
        return timeDifference;
    }

    private void displayCurrentLives(Player player) {
        int savedLives = this.playerKeepInventoryMap.getOrDefault(player.getUniqueId(), Integer.valueOf(0)).intValue();
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
        long lastQuit = this.lastQuitTimestamp.getOrDefault(playerUUID, Long.valueOf(0L)).longValue();
        long timeDifference = now - Math.max(lastLogin, lastQuit);
        long timeOnline = now - this.lastLoginTimestamp.getOrDefault(playerUUID, Long.valueOf(now)).longValue();
        timeDifference += timeOnline;
        if (timeDifference >= this.joinTimer) {
            int currentLives = this.playerKeepInventoryMap.get(playerUUID).intValue();
            int newLives = currentLives + this.livesOnJoin;
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(newLives));
            player.sendMessage(ChatColor.WHITE + "You received " + ChatColor.LIGHT_PURPLE + this.livesOnJoin + " KeepInventory " + ChatColor.WHITE + getLifeWordForm(this.livesOnJoin) + ". You now have " + ChatColor.LIGHT_PURPLE + newLives + ChatColor.WHITE + " " + getLifeWordForm(newLives) + ".");
        } else {
            displayCurrentLives(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();
        int remainingLives = this.playerKeepInventoryMap.getOrDefault(playerUUID, Integer.valueOf(0)).intValue();
        if (remainingLives > 0) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            event.getDrops().clear();
            this.playerKeepInventoryMap.put(playerUUID, Integer.valueOf(remainingLives - 1));
            getPlayerDataConfig().set(playerUUID + ".lives", Integer.valueOf(remainingLives - 1));
            savePlayerDataConfig();
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                if (remainingLives - 1 == 0) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "KeepInventory" + ChatColor.RED + " Deactivated.");
                } else {
                    player.sendMessage(ChatColor.WHITE + "You have " + ChatColor.LIGHT_PURPLE + (remainingLives - 1) + ChatColor.WHITE + " " + getLifeWordForm(remainingLives - 1) + " left.");
                }
            }, 30L);
        } else {
            event.setKeepInventory(false);
        }
        if (!player.hasMetadata("keepInventoryWarningShown") && player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY).booleanValue()) {
            player.setMetadata("keepInventoryWarningShown", new FixedMetadataValue(this, Boolean.valueOf(true)));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        int currentLives = this.playerKeepInventoryMap.get(playerUUID).intValue();
        getPlayerDataConfig().set("players." + playerUUID + ".lives", Integer.valueOf(currentLives));
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
