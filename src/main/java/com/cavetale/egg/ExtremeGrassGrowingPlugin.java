package com.cavetale.egg;

import com.cavetale.core.util.Json;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtremeGrassGrowingPlugin extends JavaPlugin {
    protected final List<Game> gameList = new ArrayList<>();
    protected final AdminCommand adminCommand = new AdminCommand(this);
    protected final EGGCommand eggCommand = new EGGCommand(this);
    protected final GameListener gameListener = new GameListener(this);
    protected Global global;
    protected File arenasFolder;
    protected File statesFolder;
    protected File globalFile;
    protected final List<String> winnerTitles = List.of(new String[] {
            "GrassGrower",
            "EGGspert",
            "Bee",
            "BuzzBee",
            "Bumblebee",
            "GrassBlock",
            "Grass",
        });

    @Override
    public void onEnable() {
        arenasFolder = new File(getDataFolder(), "arenas");
        statesFolder = new File(getDataFolder(), "states");
        globalFile = new File(getDataFolder(), "global.json");
        arenasFolder.mkdirs();
        statesFolder.mkdirs();
        getCommand("egga").setExecutor(adminCommand);
        getCommand("egg").setExecutor(eggCommand);
        getServer().getPluginManager().registerEvents(gameListener, this);
        loadGames();
    }

    @Override
    public void onDisable() {
        unloadGames();
    }

    protected void loadGames() {
        global = Json.load(globalFile, Global.class, Global::new);
        File[] fileArray = arenasFolder.listFiles();
        List<File> files = fileArray != null ? List.of(fileArray) : List.of();
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (!name.endsWith(".json")) continue;
            name = name.substring(0, name.length() - 5);
            Game game = new Game(this, name);
            game.loadArena();
            game.loadState();
            gameList.add(game);
            game.enable();
        }
    }

    protected void unloadGames() {
        for (Game game : gameList) {
            game.saveState();
            game.disable();
        }
        gameList.clear();
    }

    protected void saveGlobal() {
        Json.save(globalFile, global, true);
    }

    protected void applyGame(Location location, Consumer<Game> consumer) {
        for (Game game : gameList) {
            if (game.isInArena(location)) consumer.accept(game);
        }
    }

    protected void applyGame(Block block, Consumer<Game> consumer) {
        for (Game game : gameList) {
            if (game.isInArena(block)) consumer.accept(game);
        }
    }

    public Game gameAt(Location location) {
        for (Game game : gameList) {
            if (game.isInArena(location)) return game;
        }
        return null;
    }

    public Game gameAt(Block block) {
        for (Game game : gameList) {
            if (game.isInArena(block)) return game;
        }
        return null;
    }

    public Game gameNamed(String name) {
        for (Game game : gameList) {
            if (game.getName().equals(name)) return game;
        }
        return null;
    }

    public Game getMainGame() {
        Game result = gameNamed(global.mainGame);
        if (result != null) return result;
        return gameList.isEmpty() ? null : gameList.get(0);
    }
}
