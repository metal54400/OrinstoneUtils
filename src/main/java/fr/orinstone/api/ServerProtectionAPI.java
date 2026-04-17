package fr.orinstone.api;

import fr.orinstone.OrinstoneUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔═══════════════════════════════════════════════════════╗
 * ║         ᴏʀɪɴꜱᴛᴏɴᴇ ꜱᴇʀᴠᴇʀ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ ᴀᴘɪ              ║
 * ║  ᴜꜱᴀɢᴇ : ServerProtectionAPI api =                   ║
 * ║    OrinstoneUtils.getProtectionAPI()                  ║
 * ╚═══════════════════════════════════════════════════════╝
 */
public class ServerProtectionAPI implements Listener {

    private final OrinstoneUtils plugin;
    private final ConnectionGuard guard;

    // IP -> liste de callbacks enregistrés par d'autres plugins
    private final List<ProtectionHook> hooks = new ArrayList<>();

    // Joueurs manuellement whitelistés par l'API
    private final Set<UUID> apiWhitelist = ConcurrentHashMap.newKeySet();

    // IPs manuellement bannies par l'API
    private final Set<String> apiBannedIPs = ConcurrentHashMap.newKeySet();

    public ServerProtectionAPI(OrinstoneUtils plugin, ConnectionGuard guard) {
        this.plugin = plugin;
        this.guard  = guard;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── HOOKS EXTERNES ─────────────────────────────────────────────────────

    /**
     * Enregistrer un hook personnalisé (autre plugin).
     * Le hook sera appelé à chaque tentative de connexion.
     * Retourner true = bloquer le joueur, false = laisser passer.
     *
     * Exemple :
     *   api.registerHook((ip, name) -> myBanList.contains(ip));
     */
    public void registerHook(ProtectionHook hook) {
        hooks.add(hook);
        plugin.getLogger().info("[API] ɴᴏᴜᴠᴇᴀᴜ ʜᴏᴏᴋ ᴅᴇ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ ᴇɴʀᴇɢɪꜱᴛʀᴇ : " + hook.getClass().getSimpleName());
    }

    public void unregisterHook(ProtectionHook hook) {
        hooks.remove(hook);
    }

    // ── WHITELIST / BAN IP ─────────────────────────────────────────────────

    /** Whitelister un joueur (UUID) : la protection l'ignorera. */
    public void whitelistPlayer(UUID uuid) {
        apiWhitelist.add(uuid);
    }

    public void unwhitelistPlayer(UUID uuid) {
        apiWhitelist.remove(uuid);
    }

    public boolean isWhitelisted(UUID uuid) {
        return apiWhitelist.contains(uuid);
    }

    /** Bannir une IP directement via l'API. */
    public void banIP(String ip) {
        apiBannedIPs.add(ip);
        plugin.getLogger().info("[API] ɪᴘ ʙᴀɴɴɪᴇ : " + ip);
    }

    public void unbanIP(String ip) {
        apiBannedIPs.remove(ip);
    }

    public boolean isIPBanned(String ip) {
        return apiBannedIPs.contains(ip) || guard.isIPBanned(ip);
    }

    // ── INFOS EN TEMPS RÉEL ────────────────────────────────────────────────

    /** Nombre de connexions actives depuis une IP. */
    public int getConnectionCount(String ip) {
        return guard.getConnectionCount(ip);
    }

    /** Liste des IPs bloquées par le guard interne. */
    public Set<String> getBlockedIPs() {
        return guard.getBlockedIPs();
    }

    /** Vérifie si un joueur est considéré comme suspect. */
    public boolean isSuspect(Player player) {
        return guard.isSuspect(player.getUniqueId());
    }

    /** Score de menace d'un joueur (0 = propre, 100+ = dangereux). */
    public int getThreatScore(Player player) {
        return guard.getThreatScore(player.getUniqueId());
    }

    // ── LISTENER LOGIN ─────────────────────────────────────────────────────

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        String ip   = event.getAddress().getHostAddress();
        String name = event.getPlayer().getName();
        UUID   uuid = event.getPlayer().getUniqueId();

        // Whitelist API
        if (apiWhitelist.contains(uuid)) return;

        // Ban IP API
        if (apiBannedIPs.contains(ip)) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    colorize(plugin.getConfig().getString("messages.kick-ip-limit",
                            "&cᴠᴏᴛʀᴇ ɪᴘ ᴇꜱᴛ ʙʟᴏQᴜᴇᴇ.")));
            return;
        }

        // Hooks externes
        for (ProtectionHook hook : hooks) {
            try {
                if (hook.shouldBlock(ip, name)) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                            colorize("&8[&bOrinstone&8]\n\n&7ᴄᴏɴɴᴇxɪᴏɴ ʀᴇꜰᴜꜱᴇᴇ ᴘᴀʀ ᴜɴ ᴍᴏᴅᴜʟᴇ ᴇxᴛᴇʀɴᴇ."));
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[API] ᴇʀʀᴇᴜʀ ᴅᴀɴꜱ ʜᴏᴏᴋ " + hook.getClass().getSimpleName() + " : " + e.getMessage());
            }
        }
    }

    private String colorize(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    // ── INTERFACE HOOK ─────────────────────────────────────────────────────

    @FunctionalInterface
    public interface ProtectionHook {
        /**
         * @param ip   Adresse IP du joueur
         * @param name Nom du joueur
         * @return true pour bloquer la connexion, false pour autoriser
         */
        boolean shouldBlock(String ip, String name);
    }
}