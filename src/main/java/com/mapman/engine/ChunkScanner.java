package com.mapman.engine;

import com.mapman.BlockPosition;
import com.mapman.Region;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk 级多目标方块扫描器。
 * 扫描 Chunk 中匹配任一目标方块的坐标并缓存结果。
 */
public final class ChunkScanner {

    /** 玩家 → 上次所在 Chunk 坐标 */
    private final Map<UUID, ChunkCoord> lastChunks = new ConcurrentHashMap<>();
    /** Chunk → 其中匹配目标方块的所有坐标缓存 */
    private final Map<ChunkCoord, Set<BlockPosition>> scanCache = new ConcurrentHashMap<>();
    /** Chunk → 对应的目标方块数据缓存（用于精确匹配） */
    private final Map<ChunkCoord, Map<BlockPosition, BlockData>> blockDataCache = new ConcurrentHashMap<>();
    /** Chunk → CE 自定义方块 ID 缓存 */
    private final Map<ChunkCoord, Map<BlockPosition, String>> ceBlockIdCache = new ConcurrentHashMap<>();

    private final RuleRegistry ruleRegistry;
    private final Region region;
    private final World targetWorld;

    public record ChunkCoord(int x, int z) {
        public static ChunkCoord fromLocation(org.bukkit.Location loc) {
            return new ChunkCoord(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }
    }

    public ChunkScanner(RuleRegistry ruleRegistry, Region region, World targetWorld) {
        this.ruleRegistry = ruleRegistry;
        this.region = region;
        this.targetWorld = targetWorld;
    }

    /**
     * 玩家移动时更新位置，返回新进入的 Chunk 列表。
     */
    public Set<ChunkCoord> onPlayerMove(UUID playerId, org.bukkit.Location from, org.bukkit.Location to) {
        ChunkCoord fromChunk = ChunkCoord.fromLocation(from);
        ChunkCoord toChunk = ChunkCoord.fromLocation(to);
        if (fromChunk.equals(toChunk)) return Collections.emptySet();

        ChunkCoord oldChunk = lastChunks.put(playerId, toChunk);
        if (oldChunk == null) return Collections.emptySet();

        return getNewChunks(oldChunk, toChunk, 3); // viewDist 由外部管理
    }

    /**
     * 记录玩家所在 Chunk。
     */
    public void setPlayerChunk(UUID playerId, ChunkCoord coord) {
        lastChunks.put(playerId, coord);
    }

    /**
     * 清除玩家跟踪信息。
     */
    public void removePlayer(UUID playerId) {
        lastChunks.remove(playerId);
    }

    /**
     * 扫描 Chunk 并返回其中匹配所有目标方块的坐标及其对应 BlockData。
     */
    public Map<BlockPosition, BlockData> scanChunk(Chunk chunk) {
        ChunkCoord coord = new ChunkCoord(chunk.getX(), chunk.getZ());
        return scanChunk(coord, chunk);
    }

    /**
     * 扫描并缓存一个 Chunk。
     */
    public Map<BlockPosition, BlockData> scanChunk(ChunkCoord coord, Chunk chunk) {
        // 检查缓存
        Map<BlockPosition, BlockData> cached = blockDataCache.get(coord);
        if (cached != null) return cached;

        if (!chunk.getWorld().equals(targetWorld)) return Collections.emptyMap();

        // AABB 检测
        int chunkWorldX = coord.x() << 4;
        int chunkWorldZ = coord.z() << 4;
        if (chunkWorldX + 15 < region.minX() || chunkWorldX > region.maxX()
                || chunkWorldZ + 15 < region.minZ() || chunkWorldZ > region.maxZ()) {
            return Collections.emptyMap();
        }

        Set<Material> targetMats = ruleRegistry.targetMaterials();
        int minY = region.minY();
        int maxY = region.maxY();
        Map<BlockPosition, BlockData> result = new HashMap<>();

        // 原版方块扫描
        if (!targetMats.isEmpty()) {
            ChunkSnapshot snapshot;
            try {
                snapshot = chunk.getChunkSnapshot();
            } catch (Exception e) {
                return Collections.emptyMap();
            }

            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int worldX = chunkWorldX + dx;
                    int worldZ = chunkWorldZ + dz;
                    if (!region.containsHorizontal(worldX, worldZ)) continue;

                    for (int y = maxY - 1; y >= minY; y--) {
                        Material type = snapshot.getBlockType(dx, y, dz);
                        if (type == Material.AIR) continue;
                        if (targetMats.contains(type)) {
                            BlockPosition pos = new BlockPosition(worldX, y, worldZ);
                            if (region.contains(worldX, y, worldZ)) {
                                result.put(pos, type.createBlockData());
                            }
                        }
                    }
                }
            }
        }

        // CE 自定义方块扫描（仅当 rules 中有 CE 目标时）
        if (!ruleRegistry.targetCustomIds().isEmpty()) {
            scanCECustomBlocks(coord, chunkWorldX, chunkWorldZ, minY, maxY, result);
        }

        // 写入缓存
        if (!result.isEmpty()) {
            blockDataCache.put(coord, Collections.unmodifiableMap(result));
            Set<BlockPosition> posSet = new HashSet<>(result.keySet());
            scanCache.put(coord, Collections.unmodifiableSet(posSet));
        }

        return result;
    }

    /**
     * 扫描 CE 自定义方块，匹配 targetCustomIds。
     * 跳过已被 vanilla 扫描找到的坐标。
     */
    private void scanCECustomBlocks(ChunkCoord coord, int chunkWorldX, int chunkWorldZ,
                                     int minY, int maxY, Map<BlockPosition, BlockData> result) {
        if (!com.mapman.MapMan.hasCraftEngine()) return;
        CEWorld ceWorld = BukkitCraftEngine.instance().worldManager().getWorld(targetWorld);
        if (ceWorld == null) return;

        CEChunk ceChunk = ceWorld.getChunkAtIfLoaded(coord.x(), coord.z());
        if (ceChunk == null) return;

        Set<String> customIds = ruleRegistry.targetCustomIds();
        Map<BlockPosition, String> ceIds = new HashMap<>();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int worldX = chunkWorldX + dx;
                int worldZ = chunkWorldZ + dz;
                if (!region.containsHorizontal(worldX, worldZ)) continue;

                for (int y = maxY - 1; y >= minY; y--) {
                    BlockPosition pos = new BlockPosition(worldX, y, worldZ);
                    if (!region.contains(worldX, y, worldZ)) continue;
                    if (result.containsKey(pos)) continue; // vanilla 已匹配

                    try {
                        ImmutableBlockState ceState = ceChunk.getBlockState(worldX, y, worldZ);
                        if (ceState == null || ceState.isEmpty()) continue;

                        String ceId = ceState.owner().keyOptional()
                                .map(k -> k.location().asString())
                                .orElse(null);
                        if (ceId == null) continue;
                        if (customIds.contains(ceId)) {
                            BlockData visualData = BlockStateUtils.fromBlockData(ceState.visualBlockState().literalObject());
                            result.put(pos, visualData);
                            ceIds.put(pos, ceId);
                        }
                    } catch (Exception ignored) {
                        // 跳过异常位置
                    }
                }
            }
        }

        if (!ceIds.isEmpty()) {
            ceBlockIdCache.put(coord, ceIds);
        }
    }

    /**
     * 获取某坐标的 CE 自定义方块 ID。
     */
    @org.jetbrains.annotations.Nullable
    public String getCeBlockId(ChunkCoord coord, BlockPosition pos) {
        Map<BlockPosition, String> ceIds = ceBlockIdCache.get(coord);
        if (ceIds == null) return null;
        return ceIds.get(pos);
    }

    /**
     * 从缓存获取 Chunk 的扫描结果（懒加载）。
     */
    public Map<BlockPosition, BlockData> getCachedOrScan(ChunkCoord coord, Chunk chunk) {
        Map<BlockPosition, BlockData> cached = blockDataCache.get(coord);
        if (cached != null) return cached;
        return scanChunk(coord, chunk);
    }

    /**
     * 预缓存一个 Chunk（启动时预热用）。
     */
    public void preCacheChunk(Chunk chunk) {
        ChunkCoord coord = new ChunkCoord(chunk.getX(), chunk.getZ());
        if (!blockDataCache.containsKey(coord)) {
            scanChunk(coord, chunk);
        }
    }

    /**
     * 清除某 Chunk 的缓存（Chunk 卸载时）。
     */
    public void invalidate(ChunkCoord coord) {
        scanCache.remove(coord);
        blockDataCache.remove(coord);
        ceBlockIdCache.remove(coord);
    }

    /** 清除所有缓存 */
    public void clearAll() {
        scanCache.clear();
        blockDataCache.clear();
        ceBlockIdCache.clear();
    }

    /**
     * 计算新进入视野的 Chunk。
     */
    public static Set<ChunkCoord> getNewChunks(ChunkCoord oldCenter, ChunkCoord newCenter, int viewDist) {
        int oldMinX = oldCenter.x() - viewDist;
        int oldMaxX = oldCenter.x() + viewDist;
        int oldMinZ = oldCenter.z() - viewDist;
        int oldMaxZ = oldCenter.z() + viewDist;

        int newMinX = newCenter.x() - viewDist;
        int newMaxX = newCenter.x() + viewDist;
        int newMinZ = newCenter.z() - viewDist;
        int newMaxZ = newCenter.z() + viewDist;

        Set<ChunkCoord> result = new HashSet<>();
        for (int x = newMinX; x <= newMaxX; x++) {
            for (int z = newMinZ; z <= newMaxZ; z++) {
                if (x < oldMinX || x > oldMaxX || z < oldMinZ || z > oldMaxZ) {
                    result.add(new ChunkCoord(x, z));
                }
            }
        }
        return result;
    }

    /** 获取所有视野内 Chunk（用于首次加入时全量扫描） */
    public static Set<ChunkCoord> getViewChunks(ChunkCoord center, int viewDist) {
        Set<ChunkCoord> result = new HashSet<>();
        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                result.add(new ChunkCoord(center.x() + dx, center.z() + dz));
            }
        }
        return result;
    }

    // ========== 统计 ==========

    public int cachedChunksCount() { return scanCache.size(); }
    public int totalPositionsCount() {
        return scanCache.values().stream().mapToInt(Set::size).sum();
    }
}
