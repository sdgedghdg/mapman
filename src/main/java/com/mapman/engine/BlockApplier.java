package com.mapman.engine;

import com.mapman.BlockPosition;
import com.mapman.Region;
import com.mapman.engine.ChunkScanner.ChunkCoord;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 方块应用器。协调规则求值、Chunk 扫描和 BlockChange 发送。
 */
public final class BlockApplier {

    private final JavaPlugin plugin;
    private final RuleRegistry ruleRegistry;
    private final ChunkScanner chunkScanner;
    private final PlayerBlockCache playerBlockCache;
    private final ChangeQueue changeQueue;
    private final Region region;
    private final World targetWorld;
    private final int viewDistance;

    public BlockApplier(JavaPlugin plugin, RuleRegistry ruleRegistry, Region region,
                        World targetWorld, int viewDistance, int maxPerTick) {
        this.plugin = plugin;
        this.ruleRegistry = ruleRegistry;
        this.region = region;
        this.targetWorld = targetWorld;
        this.viewDistance = viewDistance;
        this.chunkScanner = new ChunkScanner(ruleRegistry, region, targetWorld);
        this.playerBlockCache = new PlayerBlockCache();
        this.changeQueue = new ChangeQueue(plugin, maxPerTick);
    }

    public ChangeQueue changeQueue() { return changeQueue; }
    public PlayerBlockCache playerBlockCache() { return playerBlockCache; }
    public ChunkScanner chunkScanner() { return chunkScanner; }

    // ========================================================================
    // 玩家事件
    // ========================================================================

    /**
     * 玩家加入：延迟后初始化。
     */
    public void onPlayerJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ChunkCoord coord = ChunkCoord.fromLocation(player.getLocation());
            chunkScanner.setPlayerChunk(player.getUniqueId(), coord);
            applyForPlayer(player);
        }, 5L);
    }

    /**
     * 玩家移动：跨 Chunk 时增量更新。
     */
    public void onPlayerMove(Player player, Location from, Location to) {
        if (!player.getWorld().equals(targetWorld)) return;

        Set<ChunkCoord> newChunks = chunkScanner.onPlayerMove(player.getUniqueId(), from, to);
        if (newChunks.isEmpty()) return;

        // 重新评估规则（条件可能随位置变化）
        List<Rule> activeRules = ruleRegistry.getActiveRules(player);
        if (activeRules.isEmpty()) return;

        for (ChunkCoord coord : newChunks) {
            Chunk chunk = targetWorld.getChunkAt(coord.x(), coord.z());
            Map<BlockPosition, BlockData> targets = chunkScanner.getCachedOrScan(coord, chunk);
            if (targets.isEmpty()) continue;

            for (Map.Entry<BlockPosition, BlockData> entry : targets.entrySet()) {
                BlockPosition pos = entry.getKey();
                if (playerBlockCache.contains(player.getUniqueId(), pos)) continue;

                // 找到匹配的替换
                BlockData replacement = findReplacement(player, pos, entry.getValue(), activeRules);
                if (replacement != null) {
                    playerBlockCache.add(player.getUniqueId(), pos, entry.getValue(), "");
                    changeQueue.enqueue(player.getUniqueId(),
                            new Location(targetWorld, pos.x(), pos.y(), pos.z()),
                            replacement);
                }
            }
        }
    }

    /**
     * 玩家退出：清理。
     */
    public void onPlayerQuit(Player player) {
        undoAll(player);
        chunkScanner.removePlayer(player.getUniqueId());
        ruleRegistry.removePlayer(player.getUniqueId());
    }

    /**
     * Chunk 加载：为附近玩家重新应用。
     */
    public void onChunkLoad(int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);
        Chunk chunk = targetWorld.getChunkAt(chunkX, chunkZ);
        Map<BlockPosition, BlockData> targets = chunkScanner.scanChunk(coord, chunk);
        if (targets.isEmpty()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(targetWorld)) continue;
            ChunkCoord pc = ChunkCoord.fromLocation(player.getLocation());
            if (Math.abs(pc.x() - chunkX) <= viewDistance && Math.abs(pc.z() - chunkZ) <= viewDistance) {
                List<Rule> activeRules = ruleRegistry.getActiveRules(player);
                if (activeRules.isEmpty()) continue;

                for (Map.Entry<BlockPosition, BlockData> entry : targets.entrySet()) {
                    BlockPosition pos = entry.getKey();
                    if (playerBlockCache.contains(player.getUniqueId(), pos)) continue;
                    BlockData replacement = findReplacement(player, pos, entry.getValue(), activeRules);
                    if (replacement != null) {
                        playerBlockCache.add(player.getUniqueId(), pos, entry.getValue(), "");
                        changeQueue.enqueue(player.getUniqueId(),
                                new Location(targetWorld, pos.x(), pos.y(), pos.z()),
                                replacement);
                    }
                }
            }
        }
    }

    // ========================================================================
    // 全量应用 / 撤销
    // ========================================================================

    /**
     * 为某玩家全量应用规则（首次加入或切换条件时）。
     */
    public void applyForPlayer(Player player) {
        if (!player.getWorld().equals(targetWorld)) return;

        List<Rule> activeRules = ruleRegistry.getActiveRules(player);
        if (activeRules.isEmpty()) return;

        ChunkCoord center = ChunkCoord.fromLocation(player.getLocation());

        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                ChunkCoord coord = new ChunkCoord(center.x() + dx, center.z() + dz);
                if (!targetWorld.isChunkLoaded(coord.x(), coord.z())) continue;
                Chunk chunk = targetWorld.getChunkAt(coord.x(), coord.z());
                Map<BlockPosition, BlockData> targets = chunkScanner.getCachedOrScan(coord, chunk);
                if (targets.isEmpty()) continue;

                for (Map.Entry<BlockPosition, BlockData> entry : targets.entrySet()) {
                    BlockPosition pos = entry.getKey();
                    if (playerBlockCache.contains(player.getUniqueId(), pos)) continue;
                    BlockData replacement = findReplacement(player, pos, entry.getValue(), activeRules);
                    if (replacement != null) {
                        playerBlockCache.add(player.getUniqueId(), pos, entry.getValue(), "");
                        changeQueue.enqueue(player.getUniqueId(),
                                new Location(targetWorld, pos.x(), pos.y(), pos.z()),
                                replacement);
                    }
                }
            }
        }

        ruleRegistry.setPlayerActiveRules(player.getUniqueId(), activeRules);
    }

    /**
     * 为某玩家全量撤销所有已应用的假方块（恢复真实方块）。
     */
    public void undoAll(Player player) {
        if (!player.getWorld().equals(targetWorld)) return;
        Map<BlockPosition, BlockData> changes = playerBlockCache.removeAll(player.getUniqueId());
        for (Map.Entry<BlockPosition, BlockData> entry : changes.entrySet()) {
            BlockPosition pos = entry.getKey();
            changeQueue.enqueue(player.getUniqueId(),
                    new Location(targetWorld, pos.x(), pos.y(), pos.z()),
                    entry.getValue());
        }
    }

    /**
     * 重新评估玩家的规则条件，如有变化则全量刷新。
     */
    public void reEvaluate(Player player) {
        if (!player.isOnline() || !player.getWorld().equals(targetWorld)) return;

        List<Rule> newActive = ruleRegistry.getActiveRules(player);
        if (ruleRegistry.haveRulesChanged(player, newActive)) {
            // 规则集变了，全量刷新
            undoAll(player);
            applyForPlayer(player);
        }
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 找匹配的替换方块。
     * 优先匹配 CE 自定义方块 ID，再按原版 material 匹配。
     */
    private BlockData findReplacement(Player player, BlockPosition pos,
                                       BlockData originalData, List<Rule> activeRules) {
        // 先检查 CE 自定义方块 ID
        ChunkCoord coord = new ChunkCoord(pos.x() >> 4, pos.z() >> 4);
        String ceBlockId = chunkScanner.getCeBlockId(coord, pos);
        if (ceBlockId != null) {
            for (Rule rule : activeRules) {
                String replaceId = rule.changes().get(ceBlockId);
                if (replaceId != null) {
                    return BlockResolver.resolve(replaceId);
                }
            }
        }

        // 再按原版 material 匹配
        String blockId = "minecraft:" + originalData.getMaterial().getKey().getKey();

        for (Rule rule : activeRules) {
            String replaceId = rule.changes().get(blockId);
            if (replaceId != null) {
                return BlockResolver.resolve(replaceId);
            }
        }
        return null;
    }

    /** 预热缓存 */
    public void preWarm() {
        int count = 0;
        for (Chunk chunk : targetWorld.getLoadedChunks()) {
            chunkScanner.preCacheChunk(chunk);
            count++;
        }
        plugin.getLogger().info("预热完成，已扫描 " + count + " 个 Chunk，缓存 "
                + chunkScanner.cachedChunksCount() + " 个含目标方块的 Chunk。");
    }

    /** 定时重新评估 */
    public void startReEvalTimer(int intervalTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                reEvaluate(player);
            }
        }, intervalTicks, intervalTicks);
    }
}
