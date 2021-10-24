package com.cavetale.egg;

import lombok.Value;

/**
 * Json file.
 */
@Value
public final class Cuboid {
    static final Cuboid ZERO = new Cuboid(Vec.ZERO, Vec.ZERO);
    public final Vec lo;
    public final Vec hi;

    boolean contains(int x, int y, int z) {
        return x >= lo.x && x <= hi.x
            && y >= lo.y && y <= hi.y
            && z >= lo.z && z <= hi.z;
    }
}
