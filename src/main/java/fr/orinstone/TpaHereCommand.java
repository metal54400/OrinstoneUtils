package fr.orinstone;

import fr.orinstone.OrinstoneUtils;
import fr.orinstone.TPAManager;
import fr.orinstone.TPARequest;
import fr.orinstone.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TpaHereCommand implements CommandExecutor, TabCompleter {

    private final OrinstoneUtils plugin;
    private final TPAManager mgr;

    public TpaHereCommand(OrinstoneUtils plugin, TPAManager mgr) {
        this.plugin = plugin; this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(MessageUtils.color(plugin.getConfig().getString("messages.player-only"))); return true; }
        Player p = (Player) sender;
        if (!p.hasPermission("orinstoneutils.tpahere")) { MessageUtils.send(p, plugin.getConfig().getString("messages.no-permission")); return true; }
        if (args.length < 1) { MessageUtils.send(p, "&7ᴜꜱᴀɢᴇ : &e/tpahere <ᴊᴏᴜᴇᴜʀ>"); return true; }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { MessageUtils.send(p, plugin.getConfig().getString("messages.no-player")); return true; }

        mgr.sendRequest(p, target, TPARequest.Type.TPAHERE);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1)
            for (Player p : Bukkit.getOnlinePlayers())
                if (!p.equals(s) && p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    list.add(p.getName());
        return list;
    }
}