package fr.orinstone;

import org.bukkit.entity.Player;
import java.util.UUID;

public class TPARequest {

    public enum Type { TPA, TPAHERE }

    private final UUID senderUUID;
    private final UUID targetUUID;
    private final String senderName;
    private final String targetName;
    private final Type type;
    private final long expiresAt;

    public TPARequest(Player sender, Player target, Type type, int timeoutSeconds) {
        this.senderUUID  = sender.getUniqueId();
        this.targetUUID  = target.getUniqueId();
        this.senderName  = sender.getName();
        this.targetName  = target.getName();
        this.type        = type;
        this.expiresAt   = System.currentTimeMillis() + (timeoutSeconds * 1000L);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /** Joueur qui va se téléporter après acceptation */
    public UUID getTeleportingUUID() {
        return type == Type.TPA ? senderUUID : targetUUID;
    }

    /** Destination après acceptation */
    public UUID getDestinationUUID() {
        return type == Type.TPA ? targetUUID : senderUUID;
    }

    public UUID getSenderUUID()  { return senderUUID; }
    public UUID getTargetUUID()  { return targetUUID; }
    public String getSenderName(){ return senderName; }
    public String getTargetName(){ return targetName; }
    public Type getType()        { return type; }
}