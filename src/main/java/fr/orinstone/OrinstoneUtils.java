package fr.orinstone;

import fr.orinstone.api.AntiCrash;
import fr.orinstone.api.AntiSpam;
import fr.orinstone.api.ConnectionGuard;
import fr.orinstone.api.ServerProtectionAPI;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

import static java.rmi.server.LogStream.log;

public class OrinstoneUtils extends JavaPlugin {
    /**
     * ╔══════════════════════════════════════════════════════╗
     * ║           ᴏʀɪɴꜱᴛᴏɴᴇ ᴜᴛɪʟꜱ  ᴠ2.0.0                  ║
     * ║  ᴛᴘᴀ ꜱʏꜱᴛᴇᴍ  +  ꜱᴇʀᴠᴇʀ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ ᴀᴘɪ              ║
     * ╚══════════════════════════════════════════════════════╝
     */

    private static OrinstoneUtils instance;

    private TPAManager        tpaManager;
    private ConnectionGuard connectionGuard;
    private AntiSpam antiSpam;
    private AntiCrash antiCrash;
    private ServerProtectionAPI protectionAPI;


    @Override
    public void onEnable() {

        saveDefaultConfig();
        String prefix = getConfig().getString("messages.prefix", "&8[&bOrinstone&8] &r");
        MessageUtils.setPrefix(prefix);
        instance = this;

        tpaManager = new TPAManager(this);

        // ── Protection ──────────────────────────────────────────────────
        connectionGuard = new ConnectionGuard(this);
        antiSpam        = new AntiSpam(this);
        antiCrash       = new AntiCrash(this, connectionGuard);
        protectionAPI   = new ServerProtectionAPI(this, connectionGuard);

        new TPAListener(this, tpaManager);

        // Création du dossier du plugin s'il n'existe pas
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        // Enregistrement des commandes
        SpawnCommand spawnExecutor = new SpawnCommand();
        getCommand("setspawn").setExecutor(spawnExecutor);
        getCommand("spawn").setExecutor(spawnExecutor);
        getCommand("tpa")      .setExecutor(new TpaCommand(this, tpaManager));
        getCommand("tpahere")  .setExecutor(new TpaHereCommand(this, tpaManager));
        getCommand("tpaccept") .setExecutor(new TpAcceptCommand(this, tpaManager));
        getCommand("tpdeny")   .setExecutor(new TpDenyCommand(this, tpaManager));

        getServer().getPluginManager().registerEvents(new CreativeItemListener(this), this);
        getLogger().info("OrinstoneUtils active !");

        TpaCommand tpaCmd = new TpaCommand(this, tpaManager);
        getCommand("tpa")    .setTabCompleter(tpaCmd);
        getCommand("tpahere").setTabCompleter(new TpaHereCommand(this, tpaManager));

        TpAcceptCommand acceptCmd = new TpAcceptCommand(this, tpaManager);
        TpDenyCommand   denyCmd   = new TpDenyCommand(this, tpaManager);
        getCommand("tpaccept").setTabCompleter(acceptCmd);
        getCommand("tpdeny")  .setTabCompleter(denyCmd);

        log("&a✔ ᴛᴘᴀ ꜱʏꜱᴛᴇᴍ ᴄʜᴀʀɢᴇ.");
        log("&a✔ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ ꜱᴇʀᴠᴇᴜʀ ᴀᴄᴛɪᴠᴇᴇ.");
        log("&a✔ ᴀᴘɪ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ ᴅɪꜱᴘᴏɴɪʙʟᴇ.");
        log("&bᴏʀɪɴꜱᴛᴏɴᴇᴜᴛɪʟꜱ v2.0.0 &7— ᴀᴄᴛɪꜰ.");
    }

    @Override
    public void onDisable() {
        getLogger().info("OrinstoneUtils desactive !");
        if (tpaManager != null) tpaManager.cancelAll();
        log("&cᴏʀɪɴꜱᴛᴏɴᴇᴜᴛɪʟꜱ ᴅᴇꜱᴀᴄᴛɪᴠᴇ.");
    }

    public static String format(String message) {
        return "§8[§6OrinstoneUtils§8] §r" + message.replace("&", "§");
    }
    public static OrinstoneUtils getInstance()           { return instance; }
    public TPAManager        getTpaManager()             { return tpaManager; }
    public ConnectionGuard   getConnectionGuard()        { return connectionGuard; }
    public ServerProtectionAPI getProtectionAPI()        { return protectionAPI; }

    private void log(String msg) {
        getLogger().info(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
    }
}

