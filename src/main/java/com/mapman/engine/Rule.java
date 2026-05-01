package com.mapman.engine;

import com.mapman.BlockPosition;

import java.util.Map;
import java.util.Set;

/**
 * 运行时规则。包含已编译的条件和替换映射。
 */
public class Rule {
    private final String id;
    private final int priority;
    private final Object condition; // Condition<Context> 或 null（CE 不可用时恒为 null）
    private final Map<String, String> changes; // target identifier → replace identifier
    private final Set<String> targetBlockIds; // 此规则涉及的所有目标方块 ID
    private final String regionName; // null = 全局生效

    // 玩家已应用的坐标缓存：playerId → applied positions
    // 用于撤销时精确恢复
    private final Map<java.util.UUID, Set<BlockPosition>> appliedBlocks = new java.util.concurrent.ConcurrentHashMap<>();

    public Rule(String id, int priority, Object condition,
                Map<String, String> changes, Set<String> targetBlockIds,
                String regionName) {
        this.id = id;
        this.priority = priority;
        this.condition = condition;
        this.changes = changes;
        this.targetBlockIds = targetBlockIds;
        this.regionName = regionName;
    }

    public String id() { return id; }
    public int priority() { return priority; }
    public Object condition() { return condition; }
    public Map<String, String> changes() { return changes; }
    public Set<String> targetBlockIds() { return targetBlockIds; }

    /** 记录此规则为某玩家应用了某坐标 */
    public void markApplied(java.util.UUID playerId, BlockPosition pos) {
        appliedBlocks.computeIfAbsent(playerId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(pos);
    }

    /** 获取此规则为某玩家应用的所有坐标 */
    public Set<BlockPosition> getApplied(java.util.UUID playerId) {
        return appliedBlocks.getOrDefault(playerId, java.util.Collections.emptySet());
    }

    /** 清除某玩家的所有应用记录 */
    public void clearApplied(java.util.UUID playerId) {
        appliedBlocks.remove(playerId);
    }

    /** 区域名，null 表示全局生效 */
    public String regionName() { return regionName; }

    /** 是否有条件（无条件规则一直生效） */
    public boolean hasCondition() {
        return condition != null;
    }
}
