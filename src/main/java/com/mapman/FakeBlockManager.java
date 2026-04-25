package com.mapman;

import com.mapman.WeatherManager.WeatherType;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假方块核心管理器。
 * <p>
 * 核心设计：
 * <ol>
 *   <li><b>Chunk 级表面水缓存</b>——每个 Chunk 的扫描结果只计算一次，写入 ConcurrentHashMap。</li>
 *   <li><b>增量 Chunk 更新</b>——玩家跨 Chunk 移动时只处理新进入视野的 Chunk，不做全量重扫。</li>
 *   <li><b>队列发送</b>——BlockChange 入 FakeBlockQueue，每 tick 最多发送 200 个。</li>
 *   <li><b>假方块缓存</b>——FakeBlockCache 防止同一位置重复发送，天气切换时利用其做撤销。</li>
 * </ol>
 */
public final class FakeBlockManager {

    /** Chunk 坐标记录 */
    public record ChunkCoord(int x, int z) {
        public static ChunkCoord fromLocation(Location loc) {
            return new ChunkCoord(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }
    }

    private final MapMan plugin;
    private final Region region;

    /** Chunk → 该 Chunk 内表面水方块坐标集合（全局缓存，永不变化） */
    private final ConcurrentHashMap<ChunkCoord, Set<BlockPosition>> waterCache = new ConcurrentHashMap<>();

    /** 玩家 → 上次所在 Chunk（用于跨 Chunk 位移检测） */
    private final ConcurrentHashMap<UUID, ChunkCoord> lastChunks = new ConcurrentHashMap<>();

    /** 伪装成的方块数据 */
    private final BlockData fakeBlockData;

    /** 目标世界 */
    private final World targetWorld;

    public FakeBlockManager(MapMan plugin, Region region, World targetWorld) {
        this.plugin = plugin;
        this.region = region;
        this.targetWorld = targetWorld;
        this.fakeBlockData = Bukkit.createBlockData(
                Material.valueOf(plugin.getConfig().getString("surface-water.replace-with", "PACKED_ICE"))
        );
    }

    // ========================================================================
    // 公开 API
    // ========================================================================

    /**
     * 玩家天气切换时调用。
     * <ul>
     *   <li>切换为 SNOW → 应用假方块</li>
     *   <li>切换为非 SNOW → 撤销假方块</li>
     * </ul>
     * 内部会清空并重建该玩家的假方块缓存。
     */
    public void onWeatherChange(Player player, WeatherType newWeather) {
        if (newWeather == WeatherType.SNOW) {
            applyFakeBlocks(player);
        } else {
            undoFakeBlocks(player);
        }
    }

    /**
     * 玩家加入时调用。
     * 延迟后读取玩家设置的天气，若是 SNOW 则应用假方块。
     */
    public void onPlayerJoin(Player player) {
        // 延迟几 tick 等待区块加载完成
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ChunkCoord current = ChunkCoord.fromLocation(player.getLocation());
            lastChunks.put(player.getUniqueId(), current);

            WeatherType weather = plugin.getWeatherManager().getPlayerWeather(player);
            if (weather == WeatherType.SNOW) {
                applyFakeBlocks(player);
            }
        }, 5L);
    }

    /**
     * 玩家移动时调用。
     * 仅在跨 Chunk 时计算增量可见 Chunk，并为其入队假方块发送。
     */
    public void onPlayerMove(Player player, Location from, Location to) {
        // 只处理目标世界
        if (!player.getWorld().equals(targetWorld)) return;
        WeatherType weather = plugin.getWeatherManager().getPlayerWeather(player);
        if (weather != WeatherType.SNOW) return;

        ChunkCoord fromChunk = ChunkCoord.fromLocation(from);
        ChunkCoord toChunk = ChunkCoord.fromLocation(to);
        if (fromChunk.equals(toChunk)) return; // 没有跨 chunk

        // 更新记录的 chunk
        ChunkCoord oldChunk = lastChunks.put(player.getUniqueId(), toChunk);
        if (oldChunk == null) return;

        // 只处理新进入视野的 chunk
        int viewDist = plugin.getViewDistance();
        Set<ChunkCoord> newChunks = getNewChunks(oldChunk, toChunk, viewDist);
        for (ChunkCoord coord : newChunks) {
            enqueueChunkFakeBlocks(player, coord);
        }
    }

    /**
     * Chunk 加载时调用。
     * 检查是否有 SNOW 玩家在其视距范围内，若有则重新入队该 Chunk 的假方块。
     * 用于兜底 Chunk 重载导致的假方块丢失。
     */
    public void onChunkLoad(int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);

        int viewDist = plugin.getViewDistance();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(targetWorld)) continue;
            if (plugin.getWeatherManager().getPlayerWeather(player) != WeatherType.SNOW) continue;
            ChunkCoord pc = ChunkCoord.fromLocation(player.getLocation());
            if (Math.abs(pc.x() - chunkX) <= viewDist && Math.abs(pc.z() - chunkZ) <= viewDist) {
                enqueueChunkFakeBlocks(player, coord);
            }
        }
    }

    /** 清空玩家跟踪数据（退出时调用） */
    public void removePlayer(Player player) {
        lastChunks.remove(player.getUniqueId());
        plugin.getFakeBlockCache().removeAll(player.getUniqueId());
    }

    // ========================================================================
    // 假方块应用/撤销
    // ========================================================================

    /** 为玩家应用 SNOW 假方块：扫描视野内所有 chunk，入队发送 */
    private void applyFakeBlocks(Player player) {
        if (!player.getWorld().equals(targetWorld)) return;
        // 清空旧缓存 —— 撤销交给 undoFakeBlocks 或者在切换前由调用方先调 undo
        plugin.getFakeBlockCache().removeAll(player.getUniqueId());

        ChunkCoord center = ChunkCoord.fromLocation(player.getLocation());
        int viewDist = plugin.getViewDistance();

        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                ChunkCoord coord = new ChunkCoord(center.x() + dx, center.z() + dz);
                enqueueChunkFakeBlocks(player, coord);
            }
        }
    }

    /** 撤销玩家的所有假方块：发送真实方块数据恢复原始外观 */
    private void undoFakeBlocks(Player player) {
        Set<BlockPosition> positions = plugin.getFakeBlockCache().removeAll(player.getUniqueId());
        World world = player.getWorld();

        for (BlockPosition pos : positions) {
            Location loc = new Location(world, pos.x(), pos.y(), pos.z());
            // 从世界读取真实方块数据发回客户端
            plugin.getFakeBlockQueue().enqueue(
                    player.getUniqueId(),
                    loc,
                    world.getBlockAt(loc).getBlockData()
            );
        }
    }

    // ========================================================================
    // Chunk 假方块入队
    // ========================================================================

    /**
     * 从全局缓存中读取某 Chunk 的表面水坐标，
     * 缓存未命中时懒加载扫描该 Chunk。
     * 检查玩家缓存（避免重复发送）后，入队发送。
     */
    private void enqueueChunkFakeBlocks(Player player, ChunkCoord coord) {
        // 懒加载：未缓存时实时扫描并写入全局缓存
        Set<BlockPosition> waterPositions = waterCache.computeIfAbsent(coord, k -> {
            if (!player.getWorld().equals(targetWorld)) return Collections.emptySet();
            return scanChunk(player.getWorld().getChunkAt(coord.x(), coord.z()));
        });
        if (waterPositions.isEmpty()) return;

        FakeBlockCache cache = plugin.getFakeBlockCache();
        UUID pid = player.getUniqueId();
        World world = player.getWorld();

        for (BlockPosition pos : waterPositions) {
            if (!cache.contains(pid, pos)) {
                cache.add(pid, pos);
                plugin.getFakeBlockQueue().enqueue(pid,
                        new Location(world, pos.x(), pos.y(), pos.z()),
                        fakeBlockData);
            }
        }
    }

    // ========================================================================
    // Chunk 扫描 —— 只执行一次并缓存
    // ========================================================================

    /**
     * 扫描某 Chunk，查找所有"表面水方块"并缓存结果。
     * <p>
     * 表面水定义：方块类型为 WATER 且正上方为 AIR。
     * 仅在目标世界且包含在 region 内的坐标被记录。
     * <p>
     * 此方法应当只被调用一次（通过确保扫描时机），
     * 如需预热缓存可在启动时遍历已加载 Chunk 调用。
     */
    public void preCacheChunk(Chunk chunk) {
        ChunkCoord coord = new ChunkCoord(chunk.getX(), chunk.getZ());
        waterCache.computeIfAbsent(coord, k -> scanChunk(chunk));
    }

    /**
     * 实际扫描一个 Chunk，返回其中符合条件的所有表面水方块坐标。
     * <p>
     * 遍历策略：逐列从上到下扫描，跳过非 WATER 方块。
     * 发现 WATER + 上方为 AIR 即记录，继续向下扫描同列其他水体。
     */
    private Set<BlockPosition> scanChunk(Chunk chunk) {
        // 确保 Chunk 属于目标世界
        if (!chunk.getWorld().equals(targetWorld)) {
            return Collections.emptySet();
        }

        // AABB 重叠检测：Chunk 的方块包围盒与 Region 是否有交集
        int chunkWorldX = chunk.getX() << 4;
        int chunkWorldZ = chunk.getZ() << 4;
        if (chunkWorldX + 15 < region.minX() || chunkWorldX > region.maxX()
                || chunkWorldZ + 15 < region.minZ() || chunkWorldZ > region.maxZ()) {
            return Collections.emptySet();
        }

        // 用快照避免阻塞主线程过久（快照是对当前状态的线程安全拷贝）
        ChunkSnapshot snapshot;
        try {
            snapshot = chunk.getChunkSnapshot();
        } catch (Exception e) {
            plugin.getLogger().warning("无法获取 Chunk 快照: " + e.getMessage());
            return Collections.emptySet();
        }

        int minY = region.minY();
        int maxY = region.maxY();
        Set<BlockPosition> result = new HashSet<>();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int worldX = chunkWorldX + dx;
                int worldZ = chunkWorldZ + dz;

                // 判断该柱状位置是否在 region 内（水平方向）
                if (!region.containsHorizontal(worldX, worldZ)) continue;

                // 从上往下扫描该柱
                for (int y = maxY - 1; y >= minY; y--) {
                    Material blockType = snapshot.getBlockType(dx, y, dz);
                    if (blockType == Material.WATER) {
                        // 检查上方是否为 AIR
                        if (y + 1 < maxY && snapshot.getBlockType(dx, y + 1, dz) == Material.AIR) {
                            // 检查完整三维坐标是否在 region 内
                            if (region.contains(worldX, y, worldZ)) {
                                result.add(new BlockPosition(worldX, y, worldZ));
                            }
                        }
                        // 继续向下扫描（可能存在多层水体）
                    }
                }
            }
        }

        if (result.isEmpty()) {
            return result;
        }

        plugin.getLogger().info("Chunk(" + chunk.getX() + "," + chunk.getZ()
                + ") 扫描到 " + result.size() + " 个表面水方块");
        return result;
    }

    // ========================================================================
    // 增量 Chunk 计算
    // ========================================================================

    /**
     * 计算从 oldChunk 移动到 newChunk 后，新进入视野的 Chunk 集合。
     * <p>
     * 思路：新视域矩形减旧视域矩形，差集即为新入 Chunk。
     * 视域半径为 viewDist 个 Chunk。
     */
    private static Set<ChunkCoord> getNewChunks(ChunkCoord oldCenter, ChunkCoord newCenter, int viewDist) {
        // 旧视域范围
        int oldMinX = oldCenter.x() - viewDist;
        int oldMaxX = oldCenter.x() + viewDist;
        int oldMinZ = oldCenter.z() - viewDist;
        int oldMaxZ = oldCenter.z() + viewDist;

        // 新视域范围
        int newMinX = newCenter.x() - viewDist;
        int newMaxX = newCenter.x() + viewDist;
        int newMinZ = newCenter.z() - viewDist;
        int newMaxZ = newCenter.z() + viewDist;

        Set<ChunkCoord> result = new HashSet<>();

        // 遍历新视域，只取不在旧视域的 Chunk
        for (int x = newMinX; x <= newMaxX; x++) {
            for (int z = newMinZ; z <= newMaxZ; z++) {
                if (x < oldMinX || x > oldMaxX || z < oldMinZ || z > oldMaxZ) {
                    result.add(new ChunkCoord(x, z));
                }
            }
        }

        return result;
    }

    /** 获取 waterCache 的大小（调试用） */
    public int cachedChunksCount() {
        return waterCache.size();
    }

    /** 获取所有缓存的表面水坐标总数（调试用） */
    public int totalWaterPositions() {
        return waterCache.values().stream().mapToInt(Set::size).sum();
    }
}
