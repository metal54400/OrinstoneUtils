package fr.orinstone;

import fr.orinstone.TPAManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * ᴛᴘᴀ ʟɪꜱᴛᴇɴᴇʀ
 * ────────────────────────────────────────
 * ᴀɴɴᴜʟᴇ ʟᴇ ᴡᴀʀᴍᴜᴘ ᴅᴇ ᴛᴇʟᴇᴘᴏʀᴛᴀᴛɪᴏɴ
 * ꜱɪ ʟᴇ ᴊᴏᴜᴇᴜʀ ᴍᴇᴜʀᴛ ᴏᴜ ꜱᴇ ᴅᴇᴄᴏɴɴᴇᴄᴛᴇ.
 */
public class TPAListener implements Listener {

    private final TPAManager mgr;

    public TPAListener(OrinstoneUtils plugin, TPAManager mgr) {
        this.mgr = mgr;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mgr.cancelWarmup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        mgr.cancelWarmup(event.getEntity().getUniqueId());
    }
}
