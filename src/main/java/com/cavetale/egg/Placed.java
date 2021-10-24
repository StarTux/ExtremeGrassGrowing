package com.cavetale.egg;

import java.util.UUID;
import lombok.Value;
import org.bukkit.entity.Player;

/**
 * Json structure.
 */
@Value
public final class Placed {
    protected final UUID owner;
    protected final String ownerName;
    protected final int x;
    protected final int y;
    protected final int z;

    public boolean isOwner(Player player) {
        return player.getUniqueId().equals(owner);
    }
}
