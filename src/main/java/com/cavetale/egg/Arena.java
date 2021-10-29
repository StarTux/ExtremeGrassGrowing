package com.cavetale.egg;

import java.util.HashSet;
import java.util.Set;

/**
 * Json file.
 */
public final class Arena {
    protected String world = "world";
    protected Cuboid area = Cuboid.ZERO;
    protected Set<Vec> grassBlocks = new HashSet<>();
    protected Set<Vec> viewerBlocks = new HashSet<>();
    protected Vec startButton;
}
