package com.cavetale.egg;

import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class Vec {
    static final Vec ZERO = new Vec(0, 0, 0);
    public final int x;
    public final int y;
    public final int z;

    public static Vec v(int x, int y, int z) {
        return new Vec(x, y, z);
    }

    public static Vec v(Block block) {
        return new Vec(block.getX(), block.getY(), block.getZ());
    }

    public static Vec v(Location location) {
        return new Vec(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Block toBlock(World w) {
        return w.getBlockAt(x, y, z);
    }

    public Vec add(int dx, int dy, int dz) {
        return v(x + dx, y + dy, z + dz);
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
