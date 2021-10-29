package com.cavetale.egg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

@RequiredArgsConstructor
public final class EGGCommand implements CommandExecutor {
    private final ExtremeGrassGrowingPlugin plugin;
    protected final Random random = ThreadLocalRandom.current();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        Game game;
        if (plugin.global.event) {
            game = plugin.getMainGame();
            if (game == null) {
                player.sendMessage(Component.text("The game is currently unavailable!", NamedTextColor.RED));
                return true;
            }
        } else {
            if (plugin.gameList.isEmpty()) {
                player.sendMessage(Component.text("The game is currently unavailable!", NamedTextColor.RED));
                return true;
            }
            List<Game> activeGames = new ArrayList<>();
            for (Game it : plugin.gameList) {
                if (it.state.gameState != GameState.PAUSE) {
                    activeGames.add(it);
                }
            }
            game = !activeGames.isEmpty()
                ? activeGames.get(random.nextInt(activeGames.size()))
                : plugin.gameList.get(random.nextInt(plugin.gameList.size()));
        }
        player.sendMessage(Component.text("Warping to EGG...", NamedTextColor.GREEN));
        game.warpPlayerOutside(player, TeleportCause.COMMAND);
        return true;
    }
}
