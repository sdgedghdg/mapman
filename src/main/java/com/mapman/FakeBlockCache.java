package com.mapman;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家已发送假方块缓存。
 * <p>
 * 职责：
 * - 防止重复发送同一位置的方块变化
 * - 切换天气时提供撤销所需的坐标列表
 * - 线程安全（ConcurrentHashMap 内层用 synchronized Set）
 */
public final class FakeBlockCache {

    /** playerUUID -> 该玩家已发送假方块的坐标集合 */
    private final Map<UUID, Set<BlockPosition>> cache = new ConcurrentHashMap<>();

    /** 检查某坐标是否已为该玩家发送过假方块 */
    public boolean contains(UUID playerId, BlockPosition pos) {
        Set<BlockPosition> set = cache.get(playerId);
        return set != null && set.contains(pos);
    }

    /** 记录某坐标已发送 */
    public void add(UUID playerId, BlockPosition pos) {
        cache.computeIfAbsent(playerId, k -> Collections.synchronizedSet(new HashSet<>()))
             .add(pos);
    }

    /** 清空某玩家的所有记录（天气切换时调用） */
    public Set<BlockPosition> removeAll(UUID playerId) {
        Set<BlockPosition> removed = cache.remove(playerId);
        return removed != null ? removed : Collections.emptySet();
    }

    /** 获取某玩家的全部假方块坐标（用于撤销） */
    public Set<BlockPosition> getAll(UUID playerId) {
        Set<BlockPosition> set = cache.get(playerId);
        return set != null ? Set.copyOf(set) : Collections.emptySet();
    }
}
