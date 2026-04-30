package com.mapman.engine;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.context.CommonConditions;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.SimpleContext;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * CraftEngine 条件求值器桥接层。
 * 将配置中的条件定义编译为 CE Condition 对象，并针对玩家求值。
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    /**
     * 从配置 Map 编译条件。
     *
     * @param conditionMap 条件配置（含 type 字段）
     * @return Condition 对象，null 表示无条件（恒真）
     */
    @Nullable
    public static Condition<Context> compile(Map<String, Object> conditionMap) {
        if (conditionMap == null || conditionMap.isEmpty()) return null;
        try {
            return CommonConditions.fromMap(conditionMap);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编译条件列表（any_of 语义：满足任一即可）。
     * 空列表 = 无条件（真）。
     */
    @Nullable
    public static Condition<Context> compileAny(java.util.List<Map<String, Object>> conditionMaps) {
        if (conditionMaps == null || conditionMaps.isEmpty()) return null;
        if (conditionMaps.size() == 1) {
            return compile(conditionMaps.get(0));
        }
        // 包装为 any_of
        try {
            java.util.List<Condition<Context>> compiled = new java.util.ArrayList<>();
            for (Map<String, Object> map : conditionMaps) {
                Condition<Context> c = compile(map);
                if (c != null) compiled.add(c);
            }
            if (compiled.isEmpty()) return null;
            if (compiled.size() == 1) return compiled.get(0);

            // 用 any_of 组合
            Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("type", "any_of");
            wrapper.put("terms", conditionMaps);
            return CommonConditions.fromMap(wrapper);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对某玩家求值条件。
     *
     * @param condition 已编译的条件，null = 恒真
     * @param bukkitPlayer Bukkit 玩家
     * @return 是否满足条件
     */
    public static boolean evaluate(@Nullable Condition<Context> condition,
                                   org.bukkit.entity.Player bukkitPlayer) {
        if (condition == null) return true;
        try {
            Player cePlayer = BukkitCraftEngine.instance().adapt(bukkitPlayer);
            Context ctx = SimpleContext.of(
                    new ContextHolder.Builder()
                            .withParameter(DirectContextParameters.PLAYER, cePlayer)
                            .build()
            );
            return condition.test(ctx);
        } catch (Exception e) {
            return false;
        }
    }
}
