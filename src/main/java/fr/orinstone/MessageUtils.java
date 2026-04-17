package fr.orinstone;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtils {

    private static String prefix = "&8[&bOrinstone&8] &r";

    public static void setPrefix(String p) { prefix = p; }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(color(prefix + message));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public static void sendNoPrefix(Player player, String message) {
        player.sendMessage(color(message));
    }
}