package keepinventoryshop;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

public class CustomSwirlingTask extends BukkitRunnable {
    private KeepInventoryShop plugin;

    private Player player;
    private ArmorStand armorStand;
    private int counter;
    private int taskID;
    public void setTaskID(int taskID) {
        this.taskID = taskID;
    }
    public CustomSwirlingTask(Player player) {
        this(null, player);
    }

    public CustomSwirlingTask(KeepInventoryShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.counter = 0;
    }

    @Override
    public void run() {

        if (counter == 0) {
            Location location = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5));
            player.getWorld().spawn(location, ArmorStand.class, stand -> {
                this.armorStand = stand;
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setGravity(false);
                stand.setCustomNameVisible(false);
                stand.setInvulnerable(true);
//possible misconfiguration
                ItemStack keepTotem = new ItemStack(Material.TOTEM_OF_UNDYING);
                ItemMeta keepTotemMeta = keepTotem.getItemMeta();

                if (keepTotemMeta != null) {
                    keepTotemMeta.setCustomModelData(116469);
                    keepTotem.setItemMeta(keepTotemMeta);
                }

                armorStand.setHelmet(keepTotem);


                double spread = 0.5D;
                player.getWorld().spawnParticle(Particle.SPELL_INSTANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 200, spread, spread, spread, 0.0D);
                player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0.0D, 1.0D, 0.0D), 200, spread, spread, spread, 0.0D, new Particle.DustOptions(Color.PURPLE, 1.0F));
                player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0.0D, 1.0D, 0.0D), 200, spread, spread, spread, 0.0D, new Particle.DustOptions(Color.WHITE, 1.0F));
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
            });
        }


        Location playerLoc = player.getLocation();
        double x, y, z;

        // Move the totem from 1 block away to 0.3 blocks away from the player's face
        double distance = 1.0;
        double height = 0.3; // Adjusted height

        x = playerLoc.getX() + player.getLocation().getDirection().getX() * distance;
        y = playerLoc.getY() + height;
        z = playerLoc.getZ() + player.getLocation().getDirection().getZ() * distance;

        Location newLoc = new Location(player.getWorld(), x, y, z);
        armorStand.teleport(newLoc);

        // Keep the totem always facing the player
        newLoc.setDirection(playerLoc.subtract(newLoc).toVector());
        float pitch = newLoc.getPitch();
        float yaw = newLoc.getYaw() + 180;
        double roll = 0;

        // Add a subtle wiggle
        double wiggleAmplitude = 0.18; // Increased wiggle amplitude
        double wiggleFrequency = 0.5;
        double wiggleX = wiggleAmplitude * Math.sin(2 * Math.PI * wiggleFrequency * counter / 20.0);
        double wiggleY = wiggleAmplitude * Math.sin(2 * Math.PI * wiggleFrequency * counter / 25.0);
        newLoc.add(wiggleX, wiggleY, 0);
        armorStand.teleport(newLoc);

        EulerAngle headPose = new EulerAngle(Math.toRadians(pitch), Math.toRadians(yaw), roll);
        armorStand.setHeadPose(headPose);

        // Fly upwards and out of the player's POV before disappearing
        if (counter >= 80) { // Start flying upwards after 4 seconds (80 ticks)
            y += 0.2 * (counter - 80); // Increase the height and fly-out speed
            newLoc.setY(y);
            armorStand.teleport(newLoc);
        }

        counter++;

        if (counter >= 100) { // 5 seconds (100 ticks)
            armorStand.remove();
            cancel();
        }
    }
}