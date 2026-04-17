package fr.orinstone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CreativeItemListener implements Listener {

    private final OrinstoneUtils plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public CreativeItemListener(OrinstoneUtils plugin) {
        this.plugin = plugin;
    }

    private void debug(Player player, String miniMessageText) {
        Component msg = mm.deserialize("<dark_gray>[<gold>OrinstoneUtils<dark_gray>] <gray>" + miniMessageText);
        player.sendMessage(msg);
        plugin.getLogger().info("[DEBUG] " + player.getName() + " | " + mm.stripTags(miniMessageText));
    }

    private boolean isOpOrModo(Player player) {
        return player.isOp() || player.hasPermission("orinstoneutils.modo");
    }

    private String toSmallCaps(String text) {
        String normal    = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String smallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            int idx = normal.indexOf(c);
            sb.append(idx >= 0 ? smallCaps.charAt(idx) : c);
        }
        return sb.toString();
    }

    private ItemStack applyReimbursedLore(ItemStack item, String staffName) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String marker = toSmallCaps("Remboursé par");
        String fullLine = toSmallCaps("Remboursé par " + staffName);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        // Vérification robuste contre les doublons
        boolean alreadyTagged = lore.stream()
                .map(mm::serialize)
                .anyMatch(s -> s.contains(marker));

        if (!alreadyTagged) {
            lore.add(mm.deserialize("<!italic><gray>" + fullLine));
            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * InventoryCreativeEvent est le SEUL événement fiable en mode créatif.
     * Il est appelé dès qu'un item apparaît ou est modifié dans l'inventaire.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeEvent(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Sécurité supplémentaire même si l'event implique souvent le créatif
        if (player.getGameMode() != GameMode.CREATIVE || !isOpOrModo(player)) return;

        // En mode créatif, l'item "résultat" de l'action est dans getCursor()
        // (l'item que le joueur vient de créer/modifier)
        ItemStack item = event.getCursor();

        if (item != null && !item.getType().isAir()) {
            // On applique le lore directement sur l'item du curseur
            applyReimbursedLore(item, player.getName());

            // Note : Pas besoin de setItemOnCursor, l'event créatif
            // modifie directement l'item que le client tente d'envoyer.

            debug(player, toSmallCaps("Lore appliqué sur <white>" + item.getType().name() + "</white>"));
        }
    }
}