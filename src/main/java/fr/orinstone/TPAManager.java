package fr.orinstone;

import fr.orinstone.MessageUtils;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TPAManager {

    private final OrinstoneUtils plugin;

    // "senderUUID:targetUUID" -> request
    private final Map<String, TPARequest> pending   = new ConcurrentHashMap<>();
    // playerUUID -> cooldown end timestamp
    private final Map<UUID, Long>         cooldowns = new ConcurrentHashMap<>();
    // playerUUID -> warmup task
    private final Map<UUID, BukkitTask>   warmups   = new ConcurrentHashMap<>();
    // playerUUID -> location snapshot (anti-move)
    private final Map<UUID, Location>     snapshots = new ConcurrentHashMap<>();

    public TPAManager(OrinstoneUtils plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    // ── ENVOYER ────────────────────────────────────────────────────────────

    public void sendRequest(Player sender, Player target, TPARequest.Type type) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            send(sender, "self-request"); return;
        }
        if (isOnCooldown(sender)) {
            send(sender, "cooldown", "{seconds}", String.valueOf(remainingCooldown(sender))); return;
        }
        int timeout = plugin.getConfig().getInt("tpa.timeout", 60);
        TPARequest req = new TPARequest(sender, target, type, timeout);
        pending.put(key(sender, target), req);

        int cd = plugin.getConfig().getInt("tpa.cooldown", 30);
        cooldowns.put(sender.getUniqueId(), System.currentTimeMillis() + cd * 1000L);

        send(sender, "tpa-sent", "{target}", target.getName());
        sendClickable(target, sender, type);
        scheduleExpiry(key(sender, target), sender, target, timeout);
    }

    // ── BOUTONS CLICABLES ──────────────────────────────────────────────────

    private void sendClickable(Player target, Player sender, TPARequest.Type type) {
        String prefix = MessageUtils.color(cfg("messages.prefix"));
        String msgKey = type == TPARequest.Type.TPA ? "messages.tpa-received" : "messages.tpahere-received";
        String body   = MessageUtils.color(cfg(msgKey).replace("{sender}", sender.getName()));

        TextComponent msg = new TextComponent(prefix + body + "\n");

        // ✔ ACCEPTER
        TextComponent yes = new TextComponent(MessageUtils.color("  &a&l[ ✔ ᴀᴄᴄᴇᴘᴛᴇʀ ]"));
        yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + sender.getName()));
        yes.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtils.color("&aᴀᴄᴄᴇᴘᴛᴇʀ ʟᴀ ᴅᴇᴍᴀɴᴅᴇ ᴅᴇ " + sender.getName())).create()));

        // ✘ REFUSER
        TextComponent no = new TextComponent(MessageUtils.color("  &c&l[ ✘ ʀᴇꜰᴜꜱᴇʀ ]"));
        no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + sender.getName()));
        no.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtils.color("&cʀᴇꜰᴜꜱᴇʀ ʟᴀ ᴅᴇᴍᴀɴᴅᴇ ᴅᴇ " + sender.getName())).create()));

        msg.addExtra(yes);
        msg.addExtra(no);
        target.spigot().sendMessage(msg);
    }

    // ── ACCEPTER ───────────────────────────────────────────────────────────

    public void acceptRequest(Player target, String senderName) {
        TPARequest req = findForTarget(target.getUniqueId(), senderName);
        if (req == null) { send(target, "no-request"); return; }

        Player sender = Bukkit.getPlayer(req.getSenderUUID());
        if (sender == null) { send(target, "no-player"); return; }

        pending.remove(key(req.getSenderUUID(), req.getTargetUUID()));

        send(sender, "tpa-accepted", "{target}", target.getName());

        Player teleporting  = Bukkit.getPlayer(req.getTeleportingUUID());
        Player destination  = Bukkit.getPlayer(req.getDestinationUUID());
        if (teleporting == null || destination == null) return;

        int warmup = plugin.getConfig().getInt("tpa.warmup", 3);
        if (warmup <= 0) doTeleport(teleporting, destination);
        else             startWarmup(teleporting, destination, warmup);
    }

    // ── REFUSER / ANNULER ──────────────────────────────────────────────────

    public void denyRequest(Player target, String senderName) {
        TPARequest req = findForTarget(target.getUniqueId(), senderName);
        if (req == null) { send(target, "no-request"); return; }

        pending.remove(key(req.getSenderUUID(), req.getTargetUUID()));

        Player sender = Bukkit.getPlayer(req.getSenderUUID());
        if (sender != null) send(sender, "tpa-denied", "{target}", target.getName());

        send(target, "tpa-cancelled");
    }

    public void cancelOutgoing(Player sender) {
        boolean found = false;
        Iterator<Map.Entry<String, TPARequest>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            TPARequest req = it.next().getValue();
            if (req.getSenderUUID().equals(sender.getUniqueId())) {
                it.remove();
                found = true;
            }
        }
        if (found) send(sender, "tpa-cancelled");
        else       send(sender, "no-request");
    }

    // ── WARMUP ─────────────────────────────────────────────────────────────

    private void startWarmup(Player teleporting, Player destination, int seconds) {
        snapshots.put(teleporting.getUniqueId(), teleporting.getLocation().clone());
        send(teleporting, "tpa-warmup", "{seconds}", String.valueOf(seconds));

        boolean cancelOnMove = plugin.getConfig().getBoolean("tpa.cancel-on-move", true);

        BukkitTask task = new BukkitRunnable() {
            int ticks = seconds;
            @Override public void run() {
                if (!teleporting.isOnline()) { cleanup(teleporting.getUniqueId()); cancel(); return; }
                if (cancelOnMove && hasMoved(teleporting)) {
                    send(teleporting, "tpa-moved");
                    cleanup(teleporting.getUniqueId()); cancel(); return;
                }
                ticks--;
                if (ticks <= 0) {
                    doTeleport(teleporting, destination);
                    cleanup(teleporting.getUniqueId()); cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        warmups.put(teleporting.getUniqueId(), task);
    }

    private void doTeleport(Player teleporting, Player destination) {
        if (!teleporting.isOnline() || !destination.isOnline()) return;
        send(teleporting, "tpa-teleporting");
        teleporting.teleport(destination.getLocation());
    }

    private boolean hasMoved(Player p) {
        Location snap = snapshots.get(p.getUniqueId());
        if (snap == null) return false;
        Location now = p.getLocation();
        return Math.abs(snap.getX() - now.getX()) > 0.15
            || Math.abs(snap.getY() - now.getY()) > 0.15
            || Math.abs(snap.getZ() - now.getZ()) > 0.15;
    }

    private void cleanup(UUID uuid) {
        BukkitTask t = warmups.remove(uuid);
        if (t != null) t.cancel();
        snapshots.remove(uuid);
    }

    // ── EXPIRATION ─────────────────────────────────────────────────────────

    private void scheduleExpiry(String reqKey, Player sender, Player target, int seconds) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!pending.containsKey(reqKey)) return;
                pending.remove(reqKey);
                if (sender.isOnline())
                    send(sender, "tpa-expired", "{target}", target.getName());
            }
        }.runTaskLater(plugin, seconds * 20L);
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override public void run() {
                pending.entrySet().removeIf(e -> e.getValue().isExpired());
            }
        }.runTaskTimerAsynchronously(plugin, 200L, 200L);
    }

    // ── HELPERS ────────────────────────────────────────────────────────────

    public List<TPARequest> getPendingForTarget(UUID targetUUID) {
        List<TPARequest> list = new ArrayList<>();
        for (TPARequest req : pending.values())
            if (req.getTargetUUID().equals(targetUUID) && !req.isExpired())
                list.add(req);
        return list;
    }

    private TPARequest findForTarget(UUID targetUUID, String senderName) {
        for (TPARequest req : pending.values())
            if (req.getTargetUUID().equals(targetUUID) && !req.isExpired())
                if (senderName == null || senderName.isEmpty() || req.getSenderName().equalsIgnoreCase(senderName))
                    return req;
        return null;
    }

    public boolean isOnCooldown(Player p) {
        Long end = cooldowns.get(p.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long remainingCooldown(Player p) {
        Long end = cooldowns.get(p.getUniqueId());
        return end == null ? 0 : Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void cancelWarmup(UUID playerUUID) {
        BukkitTask task = warmups.remove(playerUUID);
        if (task != null) task.cancel();
        snapshots.remove(playerUUID);
    }

    public void cancelAll() {
        warmups.values().forEach(BukkitTask::cancel);
        warmups.clear(); pending.clear();
    }

    private String key(Player a, Player b) { return a.getUniqueId() + ":" + b.getUniqueId(); }
    private String key(UUID a, UUID b)      { return a + ":" + b; }

    private String cfg(String path) { return plugin.getConfig().getString(path, ""); }

    private void send(Player p, String msgKey, String... replacements) {
        String msg = cfg("messages." + msgKey);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            msg = msg.replace(replacements[i], replacements[i + 1]);
        MessageUtils.send(p, msg);
    }
}
