package com.cavetale.egg;

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        Game game = plugin.getMainGame();
        if (game == null) {
            player.sendMessage(Component.text("The game is currently unavailable!", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("Warping to EGG...", NamedTextColor.GREEN));
        game.warpPlayerOutside(player, TeleportCause.COMMAND);
        return true;
    }
}
