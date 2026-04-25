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
 * <p>
 * 处理玩家加入、移动、退出、Chunk 加载以及方块保护事件。
 * 使用 LOWEST 优先级的拦截类事件（BlockBreak）以尽早执行。
 */
public final class PlayerListener implements Listener {

    private final MapMan plugin;

    public PlayerListener(MapMan plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家加入：延迟初始化天气和假方块。
     * 实际处理交给 FakeBlockManager#onPlayerJoin（内带 5tick 延迟）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getFakeBlockManager().onPlayerJoin(event.getPlayer());
    }

    /**
     * 玩家移动：仅在跨 Chunk 时触发增量更新。
     * 使用 MONITOR 优先级，不修改事件。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 忽略纯视角变化（没有方块级位移）
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        plugin.getFakeBlockManager().onPlayerMove(
                event.getPlayer(),
                event.getFrom(),
                event.getTo()
        );
    }

    /**
     * 玩家退出：清理跟踪数据并保存天气设置。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getFakeBlockManager().removePlayer(event.getPlayer());
        plugin.getWeatherManager().savePlayer(event.getPlayer());
    }

    /**
     * Chunk 加载：为附近 SNOW 玩家重新应用假方块。
     * 兜底 Chunk 重载（如 WorldEdit 操作后）导致的假方块丢失。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getFakeBlockManager().onChunkLoad(
                event.getChunk().getX(),
                event.getChunk().getZ()
        );
    }

    /**
     * 保护区域：禁止玩家在区域内破坏方块（有 mapman.build 权限的除外）。
     */
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
