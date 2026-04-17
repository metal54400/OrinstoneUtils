package fr.orinstone.api;

import fr.orinstone.MessageUtils;
import fr.orinstone.OrinstoneUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ᴀɴᴛɪ ꜱᴘᴀᴍ
 * ─────────────────────────────────────────
 * • ʟɪᴍɪᴛᴇ ʟᴇ ᴄʜᴀᴛ (ᴍᴇꜱꜱᴀɢᴇꜱ/ꜱᴇᴄᴏɴᴅᴇ)
 * • ʟɪᴍɪᴛᴇ ʟᴇꜱ ᴄᴏᴍᴍᴀɴᴅᴇꜱ (ᴄᴏᴍᴍᴀɴᴅᴇꜱ/ꜱᴇᴄᴏɴᴅᴇ)
 * • ᴀᴄᴛɪᴏɴ ᴄᴏɴꜰɪɢᴜʀᴀʙʟᴇ : KICK | MUTE | WARN
 */
public class AntiSpam implements Listener {

    private final OrinstoneUtils plugin;

    // UUID -> timestamps des messages
    private final Map<UUID, Deque<Long>> chatTimestamps    = new ConcurrentHashMap<>();
    // UUID -> timestamps des commandes
    private final Map<UUID, Deque<Long>> commandTimestamps = new ConcurrentHashMap<>();
    // UUID -> muted jusqu'à (timestamp)
    private final Map<UUID, Long>        muted             = new ConcurrentHashMap<>();

    public AntiSpam(OrinstoneUtils plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCleanup();
    }

    // ── CHAT ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("protection.anti-spam.enabled", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("orinstoneutils.bypass.protection")) return;

        if (isMuted(p)) {
            event.setCancelled(true);
            MessageUtils.send(p, "&7ᴠᴏᴜꜱ ᴇᴛᴇꜱ ᴍᴜᴛᴇ ᴛᴇᴍᴘᴏʀᴀɪʀᴇᴍᴇɴᴛ ᴘᴏᴜʀ ꜱᴘᴀᴍ.");
            return;
        }

        int maxChat = plugin.getConfig().getInt("protection.rate-limit.chat", 3);
        if (isSpamming(chatTimestamps, p.getUniqueId(), maxChat, 1000)) {
            event.setCancelled(true);
            handleSpam(p, "ᴄʜᴀᴛ");
        }
    }

    // ── COMMANDES ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("protection.anti-spam.enabled", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("orinstoneutils.bypass.protection")) return;

        int maxCmds = plugin.getConfig().getInt("protection.rate-limit.commands", 5);
        if (isSpamming(commandTimestamps, p.getUniqueId(), maxCmds, 1000)) {
            event.setCancelled(true);
            handleSpam(p, "ᴄᴏᴍᴍᴀɴᴅᴇꜱ");
        }
    }

    // ── LOGIQUE SPAM ──────────────────────────────────────────────────────

    private boolean isSpamming(Map<UUID, Deque<Long>> map, UUID uuid, int maxPerSecond, long windowMs) {
        Deque<Long> times = map.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.pollFirst();
        return times.size() > maxPerSecond;
    }

    private void handleSpam(Player p, String context) {
        String action = plugin.getConfig().getString("protection.anti-spam.action", "KICK").toUpperCase();
        plugin.getLogger().warning("[AntiSpam] " + p.getName() + " — ꜱᴘᴀᴍ ᴅᴇ " + context + " (" + action + ")");

        switch (action) {
            case "BAN":
                p.kickPlayer(MessageUtils.color(plugin.getConfig().getString("messages.kick-spam")));
                p.getServer().getBanList(org.bukkit.BanList.Type.NAME)
                        .addBan(p.getName(), "ᴀᴜᴛᴏ-ʙᴀɴ : ꜱᴘᴀᴍ", null, "OrinstoneUtils");
                break;
            case "MUTE":
                muted.put(p.getUniqueId(), System.currentTimeMillis() + 30_000); // 30s
                MessageUtils.send(p, "&7ᴠᴏᴜꜱ ᴀᴠᴇᴢ ᴇᴛᴇ ᴍᴜᴛᴇ &e30ꜱ &7ᴘᴏᴜʀ ꜱᴘᴀᴍ.");
                break;
            case "WARN":
                MessageUtils.send(p, "&7ᴀᴠᴇʀᴛɪꜱꜱᴇᴍᴇɴᴛ : ꜱᴛᴏᴘ ꜱᴘᴀᴍ.");
                break;
            default: // KICK
                p.kickPlayer(MessageUtils.color(plugin.getConfig().getString("messages.kick-spam")));
                break;
        }
    }

    private boolean isMuted(Player p) {
        Long end = muted.get(p.getUniqueId());
        if (end == null) return false;
        if (System.currentTimeMillis() > end) { muted.remove(p.getUniqueId()); return false; }
        return true;
    }

    // ── CLEANUP ───────────────────────────────────────────────────────────

    private void startCleanup() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                chatTimestamps.values().forEach(d -> d.removeIf(t -> now - t > 5000));
                commandTimestamps.values().forEach(d -> d.removeIf(t -> now - t > 5000));
                muted.entrySet().removeIf(e -> now > e.getValue());
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 100L);
    }
}
