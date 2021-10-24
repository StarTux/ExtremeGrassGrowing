package com.cavetale.egg;

import java.util.ArrayList;
import java.util.List;

/**
 * Json file.
 */
public final class State {
    protected GameState gameState = GameState.PAUSE;
    protected List<Placed> placedSigns = new ArrayList<>();
    protected List<String> winners = new ArrayList<>();
    protected boolean snow = false;
    protected List<Vec> spreadOptions = new ArrayList<>();
    protected boolean signOption = false;
    protected long placeStarted;
    protected long endStarted;
}
