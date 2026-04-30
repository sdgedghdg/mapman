package com.mapman.listener;

import com.mapman.MapMan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * 事件监听器。
 */
public final class PlayerListener implements Listener {

    private final MapMan plugin;

    public PlayerListener(MapMan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getBlockApplier().onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        plugin.getBlockApplier().onPlayerMove(
                event.getPlayer(),
                event.getFrom(),
                event.getTo()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getBlockApplier().onPlayerQuit(event.getPlayer());
        plugin.getWeatherManager().savePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getBlockApplier().onChunkLoad(
                event.getChunk().getX(),
                event.getChunk().getZ()
        );
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("mapman.build")) return;
        if (plugin.getRegion() == null) return;
        org.bukkit.block.Block block = event.getBlock();
        if (plugin.getRegion().contains(
                block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
        }
    }
}
