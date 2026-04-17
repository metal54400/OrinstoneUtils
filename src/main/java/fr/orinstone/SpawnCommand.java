package fr.orinstone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;

public class SpawnCommand implements CommandExecutor {

    private final File spawnFile;
    private FileConfiguration spawnConfig;

    public SpawnCommand() {
        this.spawnFile = new File(OrinstoneUtils.getInstance().getDataFolder(), "spawn.dat");
        this.spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Seul un joueur peut executer cette commande.");
            return true;
        }

        Player player = (Player) sender;

        if (label.equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("orinstoneutils.admin")) {
                player.sendMessage(OrinstoneUtils.format("§cVous n'avez pas la permission."));
                return true;
            }

            Location loc = player.getLocation();
            spawnConfig.set("spawn.world", loc.getWorld().getName());
            spawnConfig.set("spawn.x", loc.getX());
            spawnConfig.set("spawn.y", loc.getY());
            spawnConfig.set("spawn.z", loc.getZ());
            spawnConfig.set("spawn.yaw", loc.getYaw());
            spawnConfig.set("spawn.pitch", loc.getPitch());

            try {
                spawnConfig.save(spawnFile);
                player.sendMessage(OrinstoneUtils.format("§aLe point de spawn a ete defini avec succes !"));
            } catch (IOException e) {
                player.sendMessage(OrinstoneUtils.format("§cErreur lors de la sauvegarde du spawn."));
                e.printStackTrace();
            }
            return true;
        }

        if (label.equalsIgnoreCase("spawn")) {
            if (!spawnConfig.contains("spawn.world")) {
                player.sendMessage(OrinstoneUtils.format("§cLe spawn n'a pas encore ete defini."));
                return true;
            }

            World world = Bukkit.getWorld(spawnConfig.getString("spawn.world"));
            double x = spawnConfig.getDouble("spawn.x");
            double y = spawnConfig.getDouble("spawn.y");
            double z = spawnConfig.getDouble("spawn.z");
            float yaw = (float) spawnConfig.getDouble("spawn.yaw");
            float pitch = (float) spawnConfig.getDouble("spawn.pitch");

            Location spawnLoc = new Location(world, x, y, z, yaw, pitch);
            player.teleport(spawnLoc);
            player.sendMessage(OrinstoneUtils.format("§eTeleportation au spawn..."));
            return true;
        }

        return false;
    }
}
