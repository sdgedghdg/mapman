package com.mapman.engine;

import com.mapman.MapMan;
import com.mapman.Region;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * 规则注册中心。加载 rules.yml，维护规则列表，提供按玩家评估的能力。
 */
public final class RuleRegistry {

    private final List<Rule> rules = new ArrayList<>();
    /** 所有规则涉及的目标方块 ID → 对应 BlockData */
    private final Map<String, BlockData> targetBlockMap = new LinkedHashMap<>();
    /** 所有规则的目标 Material 集合（用于 Chunk 扫描） */
    private Set<Material> targetMaterials = Collections.emptySet();
    /** 所有规则的目标 CE 方块 ID 集合 */
    private Set<String> targetCustomIds = Collections.emptySet();
    /** 玩家 → 当前生效的规则 ID 集合 */
    private final Map<UUID, Set<String>> playerActiveRules = new HashMap<>();

    private boolean loaded = false;

    /**
     * 从 rules.yml 加载规则。
     */
    public void load(File configFile) {
        rules.clear();
        targetBlockMap.clear();

        if (!configFile.exists()) {
            loaded = true;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            loaded = true;
            return;
        }

        for (String ruleId : rulesSection.getKeys(false)) {
            ConfigurationSection section = rulesSection.getConfigurationSection(ruleId);
            if (section == null) continue;

            int priority = section.getInt("priority", 0);

            // 条件（仅在 CE 可用时编译，否则 condition 恒为 null = 恒真）
            Object condition = null;
            if (MapMan.hasCraftEngine()) {
                List<Map<?, ?>> rawConditions = section.getMapList("conditions");
                if (!rawConditions.isEmpty()) {
                    List<Map<String, Object>> typed = new ArrayList<>();
                    for (Map<?, ?> raw : rawConditions) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) raw;
                        typed.add(m);
                    }
                    condition = ConditionEvaluator.compileAny(typed);
                }
            }

            // 替换映射
            ConfigurationSection changesSection = section.getConfigurationSection("changes");
            Map<String, String> changes = new LinkedHashMap<>();
            if (changesSection != null) {
                for (String key : changesSection.getKeys(false)) {
                    String value = changesSection.getString(key);
                    if (value != null) {
                        changes.put(key, value);
                        // 预解析并缓存 target BlockData
                        BlockData targetData = BlockResolver.resolve(key);
                        if (targetData != null) {
                            targetBlockMap.put(key, targetData);
                        }
                    }
                }
            }

            if (changes.isEmpty()) continue;

            Rule rule = new Rule(ruleId, priority, condition, changes, changes.keySet());
            rules.add(rule);
        }

        // 按优先级降序排列
        rules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        // 构建目标集合
        Set<Material> mats = new HashSet<>();
        Set<String> customIds = new HashSet<>();
        for (String id : targetBlockMap.keySet()) {
            Material mat = BlockResolver.resolveTargetMaterial(id);
            if (mat != null) {
                mats.add(mat);
            }
            if (BlockResolver.isCustomBlockId(id)) {
                customIds.add(id);
            }
        }
        this.targetMaterials = Collections.unmodifiableSet(mats);
        this.targetCustomIds = Collections.unmodifiableSet(customIds);

        loaded = true;
    }

    /**
     * 判断某玩家在指定坐标应显示什么替换方块。
     * 遍历规则（按优先级降序），第一条匹配的规则中若含该目标方块则返回替换值。
     */
    @Nullable
    public BlockData getReplacement(org.bukkit.entity.Player player, String targetBlockId) {
        if (!loaded || rules.isEmpty()) return null;

        for (Rule rule : rules) {
            String replaceId = rule.changes().get(targetBlockId);
            if (replaceId == null) continue;
            if (!rule.hasCondition() || ConditionEvaluator.evaluate(rule.condition(), player)) {
                return BlockResolver.resolve(replaceId);
            }
        }
        return null;
    }

    /**
     * 获取某玩家当前应激活的规则集（条件求值后）。
     */
    public List<Rule> getActiveRules(org.bukkit.entity.Player player) {
        if (!loaded || rules.isEmpty()) return Collections.emptyList();

        List<Rule> active = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.hasCondition() || ConditionEvaluator.evaluate(rule.condition(), player)) {
                active.add(rule);
            }
        }
        return active;
    }

    /**
     * 比较玩家规则集是否发生变化。用于触发重新应用。
     */
    public boolean haveRulesChanged(org.bukkit.entity.Player player, List<Rule> newActive) {
        Set<String> oldIds = playerActiveRules.get(player.getUniqueId());
        if (oldIds == null) return true;
        Set<String> newIds = new HashSet<>();
        for (Rule r : newActive) newIds.add(r.id());
        return !oldIds.equals(newIds);
    }

    /** 记录玩家当前激活的规则 ID */
    public void setPlayerActiveRules(UUID playerId, List<Rule> active) {
        Set<String> ids = new HashSet<>();
        for (Rule r : active) ids.add(r.id());
        playerActiveRules.put(playerId, ids);
    }

    /** 清除某玩家的规则记录 */
    public void removePlayer(UUID playerId) {
        playerActiveRules.remove(playerId);
    }

    // ========== 查询 ==========

    /** 获取所有目标方块 Material（用于 Chunk 快速扫描） */
    public Set<Material> targetMaterials() { return targetMaterials; }

    /** 获取所有目标 CE 方块 ID */
    public Set<String> targetCustomIds() { return targetCustomIds; }

    /** 获取某目标 ID 对应的检测用 Material（扫描用） */
    @Nullable
    public Material targetMaterialFor(String blockId) {
        BlockData data = targetBlockMap.get(blockId);
        if (data != null) return data.getMaterial();
        return null;
    }

    /** 获取目标 ID → BlockData 映射（扫描时精确匹配用） */
    public Map<String, BlockData> targetBlockMap() { return targetBlockMap; }

    /** 所有已加载规则 */
    public List<Rule> rules() { return Collections.unmodifiableList(rules); }

    /** 是否已加载 */
    public boolean isLoaded() { return loaded; }

    /** 获取目标 ID 集合 */
    public Set<String> targetBlockIds() { return targetBlockMap.keySet(); }

    /** 根据目标 BlockData 查找匹配的目标 ID */
    @Nullable
    public String findTargetId(BlockData blockData) {
        for (Map.Entry<String, BlockData> entry : targetBlockMap.entrySet()) {
            if (entry.getValue().equals(blockData)) return entry.getKey();
        }
        return null;
    }
}
