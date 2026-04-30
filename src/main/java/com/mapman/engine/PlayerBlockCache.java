package com.mapman.engine;

import com.mapman.BlockPosition;
import org.bukkit.block.data.BlockData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家级方块变化追踪器。
 * 记录每个玩家已发送的 BlockChange，用于后续撤销。
 */
public final class PlayerBlockCache {

    /** playerId → (position → 真实方块的 BlockData，用于恢复) */
    private final Map<UUID, Map<BlockPosition, BlockData>> appliedChanges = new ConcurrentHashMap<>();
    /** playerId → (position → 所属的规则 ID) */
    private final Map<UUID, Map<BlockPosition, String>> changeOwners = new ConcurrentHashMap<>();

    /**
     * 记录某玩家在某位置应用了替换。
     */
    public void add(UUID playerId, BlockPosition pos, BlockData originalData, String ruleId) {
        appliedChanges.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(pos, originalData);
        changeOwners.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(pos, ruleId);
    }

    /**
     * 检查某位置是否已为该玩家发送过。
     */
    public boolean contains(UUID playerId, BlockPosition pos) {
        Map<BlockPosition, BlockData> map = appliedChanges.get(playerId);
        return map != null && map.containsKey(pos);
    }

    /**
     * 获取某玩家的所有已应用变更（用于全量撤销）。
     */
    public Map<BlockPosition, BlockData> getAll(UUID playerId) {
        return appliedChanges.getOrDefault(playerId, Collections.emptyMap());
    }

    /**
     * 清除并返回某玩家的所有变更（用于全量撤销）。
     */
    public Map<BlockPosition, BlockData> removeAll(UUID playerId) {
        Map<BlockPosition, BlockData> removed = appliedChanges.remove(playerId);
        changeOwners.remove(playerId);
        return removed != null ? removed : Collections.emptyMap();
    }

    /**
     * 清除某玩家属于指定规则的所有变更。
     */
    public Map<BlockPosition, BlockData> removeByRule(UUID playerId, String ruleId) {
        Map<BlockPosition, BlockData> all = appliedChanges.get(playerId);
        Map<BlockPosition, String> owners = changeOwners.get(playerId);
        if (all == null || owners == null) return Collections.emptyMap();

        Map<BlockPosition, BlockData> removed = new HashMap<>();
        Iterator<Map.Entry<BlockPosition, String>> it = owners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPosition, String> entry = it.next();
            if (entry.getValue().equals(ruleId)) {
                BlockPosition pos = entry.getKey();
                BlockData original = all.remove(pos);
                if (original != null) {
                    removed.put(pos, original);
                }
                it.remove();
            }
        }
        return removed;
    }

    /** 获取某玩家的变更数量 */
    public int size(UUID playerId) {
        return appliedChanges.getOrDefault(playerId, Collections.emptyMap()).size();
    }

    /** 清空所有数据 */
    public void clearAll() {
        appliedChanges.clear();
        changeOwners.clear();
    }
}
