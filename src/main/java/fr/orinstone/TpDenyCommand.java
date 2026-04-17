package fr.orinstone;

import fr.orinstone.OrinstoneUtils;
import fr.orinstone.TPAManager;
import fr.orinstone.TPARequest;
import fr.orinstone.MessageUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TpDenyCommand implements CommandExecutor, TabCompleter {

    private final OrinstoneUtils plugin;
    private final TPAManager mgr;

    public TpDenyCommand(OrinstoneUtils plugin, TPAManager mgr) {
        this.plugin = plugin; this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(MessageUtils.color(plugin.getConfig().getString("messages.player-only"))); return true; }
        Player p = (Player) sender;

        // /tpcancel = annuler une demande SORTANTE
        if (label.equalsIgnoreCase("tpcancel")) {
            mgr.cancelOutgoing(p);
            return true;
        }

        // /tpdeny ou /tpno = refuser une demande ENTRANTE
        String senderName = args.length > 0 ? args[0] : null;
        mgr.denyRequest(p, senderName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> result = new ArrayList<>();
        if (s instanceof Player && args.length == 1) {
            Player p = (Player) s;
            for (TPARequest req : mgr.getPendingForTarget(p.getUniqueId()))
                if (req.getSenderName().toLowerCase().startsWith(args[0].toLowerCase()))
                    result.add(req.getSenderName());
        }
        return result;
    }
}