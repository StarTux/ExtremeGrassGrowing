package com.cavetale.egg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Json file.
 */
public final class Global {
    protected boolean event;
    protected String mainGame = "";
    protected boolean debug = false;
    protected Map<UUID, Integer> scores = new HashMap<>();

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int value) {
        if (value == 0) return;
        int score = scores.getOrDefault(uuid, 0);
        scores.put(uuid, score + value);
    }
}
