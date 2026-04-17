package fr.orinstone.api;

import fr.orinstone.OrinstoneUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ᴀɴᴛɪ ᴄʀᴀꜱʜ
 * ─────────────────────────────────────────
 * ᴅᴇᴛᴇᴄᴛᴇ ᴇᴛ ʙʟᴏQᴜᴇ :
 * • ᴄʟɪᴄᴋꜱ ᴅ'ɪɴᴠᴇɴᴛᴀɪʀᴇ ᴀɴᴏʀᴍᴀᴜx (ᴄʀᴀꜱʜᴇʀꜱ)
 * • ᴛᴇxᴛᴇꜱ ᴅᴇ ᴄʜᴀᴛ ᴛʀᴏᴘ ʟᴏɴɢꜱ / ᴄᴀʀᴀᴄᴛᴇʀᴇꜱ ᴅᴀɴɢᴇʀᴇᴜx
 * • ᴛᴇʟᴇᴘᴏʀᴛᴀᴛɪᴏɴꜱ ᴀʙᴜꜱɪᴠᴇꜱ ʜᴏʀꜱ ᴍᴏɴᴅᴇ
 * • ᴀᴄᴛɪᴏɴꜱ ɪɴᴠᴇɴᴛᴀɪʀᴇ ᴛʀᴏᴘ ʀᴀᴘɪᴅᴇꜱ
 */
public class AntiCrash implements Listener {

    private final OrinstoneUtils plugin;
    private final ConnectionGuard guard;

    // UUID -> timestamps des clicks inventaire
    private final Map<UUID, Deque<Long>> invClicks    = new ConcurrentHashMap<>();
    // UUID -> timestamps des moves
    private final Map<UUID, Deque<Long>> moveCounts   = new ConcurrentHashMap<>();

    // Caractères qui peuvent crasher le client/serveur
    private static final String[] DANGEROUS_CHARS = {
            "\u0000", "\uFFFD", "\u202E", "\u200B", "\uD800", "\uDFFF"
    };

    public AntiCrash(OrinstoneUtils plugin, ConnectionGuard guard) {
        this.plugin = plugin;
        this.guard  = guard;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCleanup();
    }

    // ── INVENTAIRE ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("protection.anti-crash", true)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        if (p.hasPermission("orinstoneutils.bypass.protection")) return;

        // Plus de 20 clicks/seconde = suspect
        if (isAbusing(invClicks, p.getUniqueId(), 20, 1000)) {
            event.setCancelled(true);
            guard.addThreat(p.getUniqueId(), 20);
            plugin.getLogger().warning("[AntiCrash] " + p.getName() + " — ᴄʟɪᴄᴋꜱ ɪɴᴠᴇɴᴛᴀɪʀᴇ ᴀʙᴜꜱɪꜰꜱ");
        }
    }

    // ── CHAT ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("protection.anti-crash", true)) return;
        Player p   = event.getPlayer();
        String msg = event.getMessage();

        // Message trop long
        if (msg.length() > 256) {
            event.setCancelled(true);
            guard.addThreat(p.getUniqueId(), 15);
            plugin.getLogger().warning("[AntiCrash] " + p.getName() + " — ᴍᴇꜱꜱᴀɢᴇ ᴛʀᴏᴘ ʟᴏɴɢ (" + msg.length() + ")");
            return;
        }

        // Caractères dangereux
        for (String dc : DANGEROUS_CHARS) {
            if (msg.contains(dc)) {
                event.setCancelled(true);
                guard.addThreat(p.getUniqueId(), 40);
                plugin.getLogger().warning("[AntiCrash] " + p.getName() + " — ᴄᴀʀᴀᴄᴛᴇʀᴇ ᴅᴀɴɢᴇʀᴇᴜx ᴅᴇᴛᴇᴄᴛᴇ");

                // Expulser si score élevé
                if (guard.getThreatScore(p.getUniqueId()) >= 60) {
                    kick(p, "kick-crash");
                }
                return;
            }
        }
    }

    // ── COMMANDES ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("protection.anti-crash", true)) return;
        String cmd = event.getMessage();

        // Commandes anormalement longues
        if (cmd.length() > 512) {
            event.setCancelled(true);
            guard.addThreat(event.getPlayer().getUniqueId(), 25);
            plugin.getLogger().warning("[AntiCrash] " + event.getPlayer().getName() + " — ᴄᴏᴍᴍᴀɴᴅᴇ ᴛʀᴏᴘ ʟᴏɴɢᴜᴇ");
        }
    }

    // ── MOVE ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("protection.anti-crash", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("orinstoneutils.bypass.protection")) return;

        // Vitesse de déplacement anormale (téléportation forcée ?)
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
        double dist = event.getFrom().distanceSquared(event.getTo());
        if (dist > 100 * 100) { // 100 blocs/tick = impossible
            event.setCancelled(true);
            guard.addThreat(p.getUniqueId(), 30);
            plugin.getLogger().warning("[AntiCrash] " + p.getName() + " — ᴅᴇᴘʟᴀᴄᴇᴍᴇɴᴛ ᴀɴᴏʀᴍᴀʟ (" + Math.sqrt(dist) + " ʙʟᴏᴄꜱ)");
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private boolean isAbusing(Map<UUID, Deque<Long>> map, UUID uuid, int maxPerWindow, long windowMs) {
        Deque<Long> times = map.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.pollFirst();
        return times.size() > maxPerWindow;
    }

    private void kick(Player p, String msgKey) {
        String raw = plugin.getConfig().getString("messages." + msgKey, "&cKick.");
        new BukkitRunnable() {
            @Override public void run() {
                p.kickPlayer(org.bukkit.ChatColor.translateAlternateColorCodes('&', raw));
            }
        }.runTask(plugin);
    }

    private void startCleanup() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                invClicks.values().forEach(d  -> d.removeIf(t -> now - t > 5000));
                moveCounts.values().forEach(d -> d.removeIf(t -> now - t > 2000));
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 100L);
    }
}
