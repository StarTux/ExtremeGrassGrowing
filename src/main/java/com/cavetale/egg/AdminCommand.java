package com.cavetale.egg;

import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import static com.cavetale.egg.Vec.v;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class AdminCommand implements TabExecutor {
    private final ExtremeGrassGrowingPlugin plugin;

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return null;
        String low = args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            return Stream.of("start", "stop",
                             "reload", "list", "tp", "create",
                             "viewer", "grass", "area", "state",
                             "clearwinners", "snow", "signs", "hi",
                             "info", "debug", "snowman", "event",
                             "clearscores", "addscore", "rewardscores",
                             "main", "startbutton")
                .filter(it -> it.contains(low))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            return plugin.gameList.stream()
                .map(Game::getName)
                .filter(it -> it.toLowerCase().contains(low))
                .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        try {
            return onCommand2(sender, args);
        } catch (CommandWarn warn) {
            warn.send(sender);
            return true;
        }
    }

    private boolean onCommand2(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        Game game = player != null ? plugin.gameAt(player.getLocation()) : null;
        if (args.length == 0) {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage("State: " + game.state.gameState);
            sender.sendMessage("Placed signs: " + game.state.placedSigns.size());
            sender.sendMessage("Winners: " + game.state.winners);
            return false;
        }
        switch (args[0]) {
        case "start": {
            if (game == null) game = plugin.getMainGame();
            if (game == null) throw new CommandWarn("Game not found!");
            if (game.getGameState() == GameState.PLACE) {
                game.setupGameState(GameState.GROW);
            } else {
                game.cleanUp();
                game.setupGameState(GameState.PLACE);
            }
            sender.sendMessage(text("Started game " + game.getName(), AQUA));
            return true;
        }
        case "stop": {
            if (game == null) game = plugin.getMainGame();
            if (game == null) throw new CommandWarn("Game not found!");
            game.cleanUp();
            game.setupGameState(GameState.PAUSE);
            sender.sendMessage(text("Stopped game " + game.getName(), YELLOW));
            return true;
        }
        case "reload": {
            plugin.unloadGames();
            plugin.loadGames();
            sender.sendMessage(Component.text("Arenas and global file reloaded!", NamedTextColor.YELLOW));
            return true;
        }
        case "list": {
            if (args.length != 1) return false;
            sender.sendMessage(Component.text("" + plugin.gameList.size() + " Arenas", NamedTextColor.YELLOW));
            for (Game it : plugin.gameList) {
                sender.sendMessage(Component.text("- " + it.getName() + " " + it.state.gameState,
                                                  NamedTextColor.YELLOW));
            }
            return true;
        }
        case "tp": {
            if (player == null) {
                sender.sendMessage(Component.text("[egga:tp] player expected", NamedTextColor.RED));
                return true;
            }
            if (args.length != 2) return false;
            Game theGame = plugin.gameNamed(args[1]);
            theGame.warpPlayerOutside(player, TeleportCause.COMMAND);
            player.sendMessage(Component.text("Teleported to " + theGame.getName(), NamedTextColor.YELLOW));
            return true;
        }
        case "create": {
            if (player == null) {
                sender.sendMessage(Component.text("[egga:create] player expected", NamedTextColor.RED));
                return true;
            }
            if (args.length != 2) return false;
            String name = args[1];
            Game oldGame = plugin.gameNamed(name);
            if (oldGame != null) {
                player.sendMessage(Component.text("Already exists: " + oldGame.getName(), NamedTextColor.RED));
                return true;
            }
            Cuboid selection = requireSelection(player);
            Game newGame = new Game(plugin, name);
            newGame.arena = new Arena();
            newGame.state = new State();
            newGame.arena.world = player.getWorld().getName();
            newGame.arena.area = selection;
            newGame.saveArena();
            newGame.saveState();
            plugin.gameList.add(newGame);
            newGame.enable();
            player.sendMessage(Component.text("Game created: " + newGame.getName(), NamedTextColor.YELLOW));
            return true;
        }
        case "viewer": {
            if (player == null) {
                sender.sendMessage("Player expected.");
                return true;
            }
            return viewerCommand(player, args, game);
        }
        case "grass": {
            if (player == null) {
                sender.sendMessage("Player expected.");
                return true;
            }
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            boolean remove = false;
            if (args.length >= 2) {
                switch (args[1]) {
                case "remove": remove = true; break;
                case "clear":
                    game.arena.grassBlocks.clear();
                    game.saveArena();
                    sender.sendMessage("Grass blocks cleared.");
                    return true;
                default: return false;
                }
            }
            Cuboid sel = requireSelection(player);
            World world = player.getWorld();
            Set<Vec> blocks = new HashSet<>();
            for (int y = sel.lo.y; y <= sel.hi.y; y += 1) {
                for (int z = sel.lo.z; z <= sel.hi.z; z += 1) {
                    for (int x = sel.lo.x; x <= sel.hi.x; x += 1) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() == Material.GRASS_BLOCK || game.dirts.contains(block.getType())) {
                            if (remove || block.getRelative(0, 1, 0).isEmpty()) {
                                blocks.add(Vec.v(x, y, z));
                            }
                        }
                    }
                }
            }
            if (remove) {
                game.arena.grassBlocks.removeAll(blocks);
            } else {
                game.arena.grassBlocks.addAll(blocks);
            }
            sender.sendMessage("" + blocks.size() + " grass blocks.");
            game.saveArena();
            return true;
        }
        case "area": {
            if (player == null) {
                sender.sendMessage(Component.text("[egga:area] player expected", NamedTextColor.RED));
                return true;
            }
            if (args.length != 2) return false;
            Game theGame = plugin.gameNamed(args[1]);
            if (theGame == null) {
                player.sendMessage(Component.text("Game not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            Cuboid selection = requireSelection(player);
            theGame.arena.area = selection;
            theGame.arena.world = player.getWorld().getName();
            theGame.saveArena();
            player.sendMessage(Component.text(theGame.getName() + ": Game area updated: " + selection,
                                              NamedTextColor.YELLOW));
            return true;
        }
        case "state": {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            if (args.length == 2) {
                GameState newState;
                try {
                    newState = GameState.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException iae) {
                    sender.sendMessage("Illegal state: " + args[1]);
                    return true;
                }
                game.cleanUp();
                game.setupGameState(newState);
                sender.sendMessage("Switched to state " + newState);
            }
            return true;
        }
        case "clearwinners": {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            game.state.winners.clear();
            game.saveState();
            sender.sendMessage("Winners cleared");
            return true;
        }
        case "snow": {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            game.state.snow = !game.state.snow;
            game.saveState();
            sender.sendMessage("Snow is now " + (game.state.snow ? "on" : "off") + ".");
            return true;
        }
        case "signs": {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(game.state.placedSigns.size() + " placed signs:");
            for (Placed placed : game.state.placedSigns) {
                sender.sendMessage(placed.getOwnerName()
                                   + " " + placed.getX()
                                   + " " + placed.getY()
                                   + " " + placed.getZ());
            }
            sender.sendMessage("");
            return true;
        }
        case "hi": {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            if (player == null) return false;
            for (Vec v: game.arena.viewerBlocks) {
                Block block = player.getWorld().getBlockAt(v.x, v.y, v.z);
                player.sendBlockChange(block.getLocation(),
                                       Material.GLOWSTONE.createBlockData());
            }
            for (Vec v: game.arena.grassBlocks) {
                Block block = player.getWorld().getBlockAt(v.x, v.y, v.z);
                player.sendBlockChange(block.getLocation(),
                                       Material.REDSTONE_BLOCK.createBlockData());
            }
            sender.sendMessage("Highlighting blocks");
            return true;
        }
        case "info": {
            sender.sendMessage(Component.text(Json.serialize(plugin.global), NamedTextColor.YELLOW));
            if (game != null) {
                sender.sendMessage(Component.text(Json.serialize(game.state), NamedTextColor.YELLOW));
            }
            return true;
        }
        case "debug": {
            plugin.global.debug = !plugin.global.debug;
            sender.sendMessage("debug mode: " + plugin.global.debug);
            plugin.saveGlobal();
            return true;
        }
        case "snowman": {
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            game.spawnSnowman(player.getLocation());
            player.sendMessage("snowman spawned");
            return true;
        }
        case "event": {
            if (args.length == 1) {
                sender.sendMessage(Component.text("Event mode: " + plugin.global.event, NamedTextColor.YELLOW));
                return true;
            }
            if (args.length != 2) return false;
            try {
                plugin.global.event = Boolean.parseBoolean(args[1]);
            } catch (IllegalArgumentException iae) {
                sender.sendMessage(Component.text("Boolean expected: " + args[1], NamedTextColor.RED));
                return true;
            }
            plugin.saveGlobal();
            sender.sendMessage(Component.text("Event mode set to " + plugin.global.event, NamedTextColor.YELLOW));
            return true;
        }
        case "clearscores": {
            plugin.global.scores.clear();
            plugin.saveGlobal();
            plugin.computeHighscore();
            sender.sendMessage(text("Scores cleared", YELLOW));
            return true;
        }
        case "addscore": {
            if (args.length != 3) return false;
            final PlayerCache target = CommandArgCompleter.requirePlayerCache(args[1]);
            final int value = CommandArgCompleter.requireInt(args[2], i -> i != 0);
            plugin.global.addScore(target.uuid, value);
            plugin.saveGlobal();
            plugin.computeHighscore();
            sender.sendMessage(text("Score of " + target.name + " now is " + plugin.global.getScore(target.uuid), YELLOW));
            return true;
        }
        case "rewardscores": {
            int result = Highscore.reward(plugin.global.scores,
                                          "extreme_grass_growing_event",
                                          TrophyCategory.HOE,
                                          ExtremeGrassGrowingPlugin.TITLE,
                                          hi -> "You collected " + hi.score + " point" + (hi.score == 1 ? "" : "s"));
            sender.sendMessage(text(result + " players rewarded", YELLOW));
            return true;
        }
        case "main": {
            if (args.length != 2) return false;
            String name = args[1];
            Game newGame = plugin.gameNamed(name);
            if (newGame == null) {
                sender.sendMessage(Component.text("Game not found: " + name, NamedTextColor.RED));
                return true;
            }
            Game oldMain = plugin.getMainGame();
            plugin.global.mainGame = name;
            plugin.saveGlobal();
            if (plugin.global.event && oldMain != null) {
                for (Player other : oldMain.getPlayers()) {
                    newGame.warpPlayerOutside(other, TeleportCause.COMMAND);
                }
            }
            sender.sendMessage(Component.text("Main game is now " + newGame.getName(), NamedTextColor.YELLOW));
            return true;
        }
        case "startbutton": {
            if (args.length != 1) return false;
            if (player == null) {
                sender.sendMessage(Component.text("[egga:tp] player expected", NamedTextColor.RED));
                return true;
            }
            if (game == null) {
                sender.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
                return true;
            }
            Cuboid selection = requireSelection(player);
            game.arena.startButton = selection.lo;
            game.saveArena();
            player.sendMessage(Component.text("Start button set to " + game.arena.startButton, NamedTextColor.YELLOW));
            return true;
        }
        case "checkforwinner":
            if (game != null) game.checkForWinner();
            return true;
        default: return false;
        }
    }

    private boolean viewerCommand(Player player, String[] args, Game game) {
        if (game == null) {
            player.sendMessage(Component.text("Extreme Grass Growing arena required!", NamedTextColor.RED));
            return true;
        }
        boolean remove = false;
        if (args.length >= 2) {
            switch (args[1]) {
            case "remove": remove = true; break;
            case "clear":
                game.arena.viewerBlocks.clear();
                game.saveArena();
                player.sendMessage("Viewer blocks cleared.");
                return true;
            default: return false;
            }
        }
        Cuboid sel = requireSelection(player);
        if (sel != null) {
            World world = player.getWorld();
            Set<Vec> blocks = new HashSet<>();
            for (int y = sel.lo.y; y <= sel.hi.y; y += 1) {
                for (int z = sel.lo.z; z <= sel.hi.z; z += 1) {
                    for (int x = sel.lo.x; x <= sel.hi.x; x += 1) {
                        Block block = world.getBlockAt(x, y, z);
                        if (remove || (block.getType().isSolid()
                                       && block.getRelative(0, 1, 0).isEmpty()
                                       && block.getRelative(0, 2, 0).isEmpty())) {
                            blocks.add(Vec.v(x, y, z));
                        }
                    }
                }
            }
            if (remove) {
                game.arena.viewerBlocks.removeAll(blocks);
            } else {
                game.arena.viewerBlocks.addAll(blocks);
            }
            player.sendMessage("" + blocks.size() + " viewer blocks.");
            game.saveArena();
        } else {
            player.sendMessage("No selection made.");
        }
        return true;
    }

    private Cuboid requireSelection(Player player) {
        com.cavetale.core.struct.Cuboid c = com.cavetale.core.struct.Cuboid.requireSelectionOf(player);
        return new Cuboid(v(c.ax, c.ay, c.az), v(c.bx, c.by, c.bz));
    }
}
