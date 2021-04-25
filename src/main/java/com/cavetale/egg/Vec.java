package com.cavetale.egg;

import lombok.Value;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class Vec {
    static final Vec ZERO = new Vec(0, 0, 0);
    public final int x;
    public final int y;
    public final int z;

    static Vec v(int x, int y, int z) {
        return new Vec(x, y, z);
    }
    static Vec v(Block block) {
        return new Vec(block.getX(), block.getY(), block.getZ());
    }
    Block toBlock(World w) {
        return w.getBlockAt(x, y, z);
    }
}
