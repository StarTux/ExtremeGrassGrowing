package com.cavetale.egg;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.sidebar.PlayerSidebarEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

@RequiredArgsConstructor
public final class GameListener implements Listener {
    private final ExtremeGrassGrowingPlugin plugin;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        plugin.applyGame(event.getPlayer().getLocation(), game -> game.onPlayerMove(event));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.applyGame(event.getBlock(), game -> game.onBlockPlace(event));
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        plugin.applyGame(event.getBlock(), game -> game.onBlockSpread(event));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerInteract(PlayerInteractEvent event) {
        plugin.applyGame(event.getPlayer().getLocation(), game -> game.onPlayerInteract(event));
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        plugin.applyGame(event.getPlayer().getLocation(), game -> game.onPlayerSidebar(event));
    }

    /**
     * Uncancel build permissions for your own sign.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        plugin.applyGame(query.getPlayer().getLocation(), game -> game.onPlayerBlockAbility(query));
    }

    /**
     * Uncancel sign change event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onSignChange(SignChangeEvent event) {
        plugin.applyGame(event.getBlock(), game -> game.onSignChange(event));
    }

    /**
     * Remove sign broken by owner.
     * Uncancel build permissions.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onBlockBreak(BlockBreakEvent event) {
        plugin.applyGame(event.getBlock(), game -> game.onBlockBreak(event));
    }

    /**
     * Remove sign broken by owner.
     * Uncancel build permissions.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        plugin.applyGame(event.getBlock(), game -> game.onPlayerBreakBlock(event));
    }

    @EventHandler(priority = EventPriority.LOW)
    void onPlayerTeleport(PlayerTeleportEvent event) {
        plugin.applyGame(event.getTo(), game -> game.onPlayerTeleport(event));
    }

    @EventHandler(priority = EventPriority.LOW)
    void onPluginPlayer(PluginPlayerEvent event) {
        plugin.applyGame(event.getPlayer().getLocation(), game -> game.onPluginPlayer(event));
    }
}
