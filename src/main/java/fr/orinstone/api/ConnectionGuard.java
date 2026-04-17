package fr.orinstone.api;

import fr.orinstone.OrinstoneUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ᴄᴏɴɴᴇᴄᴛɪᴏɴ ɢᴜᴀʀᴅ
 * ─────────────────────────────────────────
 * ᴘʀᴏᴛᴇᴄᴛɪᴏɴꜱ :
 * • ʟɪᴍɪᴛᴇ ᴅᴇ ᴄᴏɴɴᴇxɪᴏɴꜱ ᴘᴀʀ ɪᴘ
 * • ᴅᴇᴛᴇᴄᴛɪᴏɴ ᴅᴇ ʙᴏᴛꜱ (ɴᴏᴍ, ᴠɪᴛᴇꜱꜱᴇ)
 * • ꜱᴄᴏʀᴇ ᴅᴇ ᴍᴇɴᴀᴄᴇ ᴘᴀʀ ᴊᴏᴜᴇᴜʀ
 * • ʙᴀɴ ᴅ'ɪᴘ ᴀᴜᴛᴏᴍᴀᴛɪQᴜᴇ
 */
public class ConnectionGuard implements Listener {

    private final OrinstoneUtils plugin;

    // IP -> nombre de joueurs connectés actuellement
    private final Map<String, Integer> activePerIP = new ConcurrentHashMap<>();
    // IP -> timestamps des dernières tentatives de connexion
    private final Map<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();
    // IP -> bloquée ?
    private final Set<String> blockedIPs = ConcurrentHashMap.newKeySet();
    // UUID -> score de menace (0–100+)
    private final Map<UUID, Integer> threatScores = new ConcurrentHashMap<>();
    // UUID -> suspect ?
    private final Set<UUID> suspects = ConcurrentHashMap.newKeySet();

    // Patterns de noms suspects (bots classiques)
    private static final Pattern BOT_NAME_PATTERN = Pattern.compile(
            "(?i)(bot|hack|crack|exploit|mcbot|autofarm|flood|spam|ghost|null|steve\\d+|alex\\d+)"
    );
    // Noms trop aléatoires : beaucoup de chiffres
    private static final Pattern RANDOM_NAME_PATTERN = Pattern.compile(".*\\d{4,}.*");

    public ConnectionGuard(OrinstoneUtils plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startDecayTask();
    }

    // ── LOGIN ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) return;

        String ip = event.getAddress().getHostAddress();
        String name = event.getPlayer().getName();
        UUID uuid = event.getPlayer().getUniqueId();

        // Bypass pour OPs
        if (event.getPlayer().isOp()) return;

        // 1. IP bloquée
        if (blockedIPs.contains(ip)) {
            deny(event, "kick-ip-limit");
            return;
        }

        // 2. Limite de connexions par IP
        int maxPerIP = plugin.getConfig().getInt("protection.max-connections-per-ip", 3);
        int current = activePerIP.getOrDefault(ip, 0);
        if (current >= maxPerIP) {
            addThreat(uuid, 30);
            if (plugin.getConfig().getBoolean("protection.auto-ban-bots", true)) blockIP(ip);
            deny(event, "kick-ip-limit");
            return;
        }

        // 3. Vitesse de connexion (flood detection)
        Deque<Long> attempts = loginAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        attempts.addLast(now);
        // Garder seulement les 10 dernières secondes
        while (!attempts.isEmpty() && now - attempts.peekFirst() > 10_000) attempts.pollFirst();

        if (attempts.size() > 5) { // plus de 5 tentatives en 10s depuis la même IP
            addThreat(uuid, 50);
            blockIP(ip);
            deny(event, "kick-bot");
            return;
        }

        // 4. Détection de nom suspect
        if (plugin.getConfig().getBoolean("protection.auto-ban-bots", true)) {
            if (BOT_NAME_PATTERN.matcher(name).find() || RANDOM_NAME_PATTERN.matcher(name).matches()) {
                addThreat(uuid, 40);
                suspects.add(uuid);
                deny(event, "kick-bot");
                return;
            }
        }

        // 5. Noms sur liste noire config
        List<String> blacklist = plugin.getConfig().getStringList("protection.blacklisted-names");
        for (String bl : blacklist) {
            if (name.toLowerCase().contains(bl.toLowerCase())) {
                deny(event, "kick-bot");
                return;
            }
        }

        // Connexion autorisée → incrémenter compteur
        activePerIP.merge(ip, 1, Integer::sum);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String ip = event.getPlayer().getAddress() != null
                ? event.getPlayer().getAddress().getAddress().getHostAddress()
                : null;
        if (ip != null) {
            activePerIP.merge(ip, -1, (a, b) -> Math.max(0, a + b));
        }
    }

    // ── SCORE DE MENACE ────────────────────────────────────────────────────

    public void addThreat(UUID uuid, int points) {
        int score = threatScores.merge(uuid, points, Integer::sum);
        if (score >= 80) suspects.add(uuid);
    }

    public void resetThreat(UUID uuid) {
        threatScores.remove(uuid);
        suspects.remove(uuid);
    }

    public int getThreatScore(UUID uuid) {
        return threatScores.getOrDefault(uuid, 0);
    }

    public boolean isSuspect(UUID uuid) {
        return suspects.contains(uuid);
    }

    // ── GESTION IPs ────────────────────────────────────────────────────────

    public void blockIP(String ip) {
        blockedIPs.add(ip);
        plugin.getLogger().warning("[Protection] ɪᴘ ʙʟᴏQᴜᴇᴇ : " + ip);
    }

    public void unblockIP(String ip) {
        blockedIPs.remove(ip);
        plugin.getLogger().info("[Protection] ɪᴘ ᴅᴇʙʟᴏQᴜᴇᴇ : " + ip);
    }

    public boolean isIPBanned(String ip) {
        return blockedIPs.contains(ip);
    }

    public Set<String> getBlockedIPs() {
        return Collections.unmodifiableSet(blockedIPs);
    }

    public int getConnectionCount(String ip) {
        return activePerIP.getOrDefault(ip, 0);
    }

    // ── DECAY (score diminue avec le temps) ────────────────────────────────

    private void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                threatScores.replaceAll((uuid, score) -> Math.max(0, score - 5));
                threatScores.entrySet().removeIf(e -> e.getValue() == 0);
                // Re-évaluer les suspects
                suspects.removeIf(uuid -> threatScores.getOrDefault(uuid, 0) < 50);
            }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L); // toutes les 30s
    }

    // ── HELPER ─────────────────────────────────────────────────────────────

    private void deny(PlayerLoginEvent event, String msgKey) {
        String raw = plugin.getConfig().getString("messages." + msgKey,
                "&8[&bOrinstone&8]\n\n&cᴄᴏɴɴᴇxɪᴏɴ ʀᴇꜰᴜꜱᴇᴇ.");
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                org.bukkit.ChatColor.translateAlternateColorCodes('&', raw));
    }
}