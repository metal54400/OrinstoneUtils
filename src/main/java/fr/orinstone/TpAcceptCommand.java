package fr.orinstone;

import fr.orinstone.OrinstoneUtils;
import fr.orinstone.TPAManager;
import fr.orinstone.TPARequest;
import fr.orinstone.MessageUtils;
import net.md_5.bungee.api.chat.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TpAcceptCommand implements CommandExecutor, TabCompleter {

    private final OrinstoneUtils plugin;
    private final TPAManager mgr;

    public TpAcceptCommand(OrinstoneUtils plugin, TPAManager mgr) {
        this.plugin = plugin; this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(MessageUtils.color(plugin.getConfig().getString("messages.player-only"))); return true; }
        Player p = (Player) sender;

        List<TPARequest> list = mgr.getPendingForTarget(p.getUniqueId());
        if (list.isEmpty()) { MessageUtils.send(p, plugin.getConfig().getString("messages.no-request")); return true; }

        // Si plusieurs demandes et aucun nom fourni → lister avec boutons
        if (list.size() > 1 && args.length == 0) {
            MessageUtils.send(p, "&7ᴘʟᴜꜱɪᴇᴜʀꜱ ᴅᴇᴍᴀɴᴅᴇꜱ ᴇɴ ᴀᴛᴛᴇɴᴛᴇ :");
            for (TPARequest req : list) sendRow(p, req);
            return true;
        }

        String senderName = args.length > 0 ? args[0] : null;
        mgr.acceptRequest(p, senderName);
        return true;
    }

    private void sendRow(Player p, TPARequest req) {
        String typeLabel = req.getType() == TPARequest.Type.TPA
                ? MessageUtils.color("&7ᴛᴘᴀ")
                : MessageUtils.color("&7ᴛᴘᴀʜᴇʀᴇ");

        TextComponent line = new TextComponent(MessageUtils.color("  &8» &e" + req.getSenderName() + " &8(" + typeLabel + "&8)  "));

        TextComponent yes = new TextComponent(MessageUtils.color("&a[ ✔ ]"));
        yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + req.getSenderName()));
        yes.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtils.color("&aᴀᴄᴄᴇᴘᴛᴇʀ")).create()));

        TextComponent no = new TextComponent(MessageUtils.color("  &c[ ✘ ]"));
        no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + req.getSenderName()));
        no.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtils.color("&cʀᴇꜰᴜꜱᴇʀ")).create()));

        line.addExtra(yes);
        line.addExtra(no);
        p.spigot().sendMessage(line);
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
