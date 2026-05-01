package com.mapman;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * 多区域管理器。
 * 管理命名区域，处理 config.yml 的读写和旧格式自动迁移。
 */
public final class RegionManager {

    private final Plugin plugin;
    private final Map<String, Region> regions = new LinkedHashMap<>();

    public RegionManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // ========== 加载 / 迁移 ==========

    /**
     * 从 config.yml 加载区域配置。
     * 若检测到旧版单区域格式，自动迁移。
     */
    public void load(File configFile) {
        regions.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection regionsSection = config.getConfigurationSection("regions");

        // 迁移旧格式
        if (regionsSection == null && config.contains("region")) {
            ConfigurationSection oldRegion = config.getConfigurationSection("region");
            if (oldRegion != null) {
                plugin.getLogger().info("检测到旧版 region 配置，正在迁移为 regions.default...");
                String world = oldRegion.getString("world", "world");
                int x1 = oldRegion.getInt("pos1.x", -100);
                int y1 = oldRegion.getInt("pos1.y", -64);
                int z1 = oldRegion.getInt("pos1.z", -100);
                int x2 = oldRegion.getInt("pos2.x", 100);
                int y2 = oldRegion.getInt("pos2.y", 320);
                int z2 = oldRegion.getInt("pos2.z", 100);

                config.set("region", null);
                config.createSection("regions.default.world");
                config.set("regions.default.world", world);
                config.set("regions.default.pos1.x", x1);
                config.set("regions.default.pos1.y", y1);
                config.set("regions.default.pos1.z", z1);
                config.set("regions.default.pos2.x", x2);
                config.set("regions.default.pos2.y", y2);
                config.set("regions.default.pos2.z", z2);

                try {
                    config.save(configFile);
                    plugin.getLogger().info("迁移完成：旧 region 已转为 regions.default。");
                } catch (Exception e) {
                    plugin.getLogger().warning("配置迁移保存失败: " + e.getMessage());
                }

                regions.put("default", new Region(world, x1, y1, z1, x2, y2, z2));
                return;
            }
        }

        // 加载新版多区域格式
        if (regionsSection == null) return;

        for (String name : regionsSection.getKeys(false)) {
            ConfigurationSection section = regionsSection.getConfigurationSection(name);
            if (section == null) continue;
            String world = section.getString("world", "world");
            int x1 = section.getInt("pos1.x", -100);
            int y1 = section.getInt("pos1.y", -64);
            int z1 = section.getInt("pos1.z", -100);
            int x2 = section.getInt("pos2.x", 100);
            int y2 = section.getInt("pos2.y", 320);
            int z2 = section.getInt("pos2.z", 100);
            regions.put(name, new Region(world, x1, y1, z1, x2, y2, z2));
        }

        plugin.getLogger().info("已加载 " + regions.size() + " 个区域: " + regions.keySet());
    }

    /**
     * 重新加载（reload 时调用）。
     */
    public void reload(File configFile) {
        plugin.reloadConfig();
        load(configFile);
    }

    // ========== 查询 ==========

    @Nullable
    public Region getRegion(String name) {
        return regions.get(name);
    }

    public Set<String> getRegionNames() {
        return Collections.unmodifiableSet(regions.keySet());
    }

    public Collection<Region> getAllRegions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    /**
     * 获取某世界所有区域的并集 bounds（用于 Chunk 扫描）。
     * 同一世界可能有多个区域，取它们的 AABB 并集。
     */
    @Nullable
    public Region getOverallBounds(String worldName) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean found = false;

        for (Region r : regions.values()) {
            if (!r.worldName().equals(worldName)) continue;
            found = true;
            if (r.minX() < minX) minX = r.minX();
            if (r.minY() < minY) minY = r.minY();
            if (r.minZ() < minZ) minZ = r.minZ();
            if (r.maxX() > maxX) maxX = r.maxX();
            if (r.maxY() > maxY) maxY = r.maxY();
            if (r.maxZ() > maxZ) maxZ = r.maxZ();
        }

        return found ? new Region(worldName, minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    /**
     * 返回坐标所在的第一个区域名称（可能为 null）。
     */
    @Nullable
    public String getRegionNameAt(String worldName, int x, int y, int z) {
        for (Map.Entry<String, Region> entry : regions.entrySet()) {
            if (entry.getValue().worldName().equals(worldName)
                    && entry.getValue().contains(x, y, z)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 检查坐标是否在任意区域中。
     */
    public boolean containsAny(String worldName, int x, int y, int z) {
        return getRegionNameAt(worldName, x, y, z) != null;
    }

    // ========== 修改 ==========

    public void addRegion(String name, Region region) {
        regions.put(name, region);
    }

    public boolean removeRegion(String name) {
        return regions.remove(name) != null;
    }

    /**
     * 将当前区域写回 config.yml。
     */
    public void save(File configFile) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // 清空旧 regions 节点重建
        config.set("regions", null);
        for (Map.Entry<String, Region> entry : regions.entrySet()) {
            String name = entry.getKey();
            Region r = entry.getValue();
            config.set("regions." + name + ".world", r.worldName());
            config.set("regions." + name + ".pos1.x", r.minX());
            config.set("regions." + name + ".pos1.y", r.minY());
            config.set("regions." + name + ".pos1.z", r.minZ());
            config.set("regions." + name + ".pos2.x", r.maxX());
            config.set("regions." + name + ".pos2.y", r.maxY());
            config.set("regions." + name + ".pos2.z", r.maxZ());
        }

        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("保存区域配置失败: " + e.getMessage());
        }
    }
}
