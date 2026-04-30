package com.mapman;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家天气状态管理器。
 * <p>
 * 内存中维护玩家天气偏好，定期/退出时写回 data.yml。
 * 线程安全（ConcurrentHashMap），但写文件仅在主线程调用。
 */
public final class WeatherManager {

    public enum WeatherType {
        CLEAR,
        RAIN,
        SNOW
    }

    private final MapMan plugin;
    private final File dataFile;
    private final Map<UUID, WeatherType> playerWeather = new ConcurrentHashMap<>();
    private boolean dirty = false;

    public WeatherManager(MapMan plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    /** 从 data.yml 加载所有玩家记录 */
    public void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (!config.contains("players")) return;

        for (String key : config.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String typeStr = config.getString("players." + key);
                WeatherType type = WeatherType.valueOf(typeStr.toUpperCase());
                playerWeather.put(uuid, type);
            } catch (Exception ignored) {
                // 跳过损坏的条目
            }
        }
        dirty = false;
    }

    /** 保存到 data.yml */
    public void save() {
        if (!dirty) return;
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, WeatherType> entry : playerWeather.entrySet()) {
            config.set("players." + entry.getKey().toString(), entry.getValue().name());
        }
        try {
            config.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存玩家天气数据: " + e.getMessage());
        }
    }

    /** 获取某玩家的天气类型 */
    public WeatherType getPlayerWeather(Player player) {
        return playerWeather.getOrDefault(player.getUniqueId(), WeatherType.valueOf(plugin.getConfig().getString("weather-default", "CLEAR")));
    }

    /** 设置某玩家的天气类型，并实时生效 */
    public void setPlayerWeather(Player player, WeatherType type) {
        UUID uuid = player.getUniqueId();
        playerWeather.put(uuid, type);
        dirty = true;

        // 同步到玩家客户端（仅视觉效果）
        switch (type) {
            case CLEAR -> player.resetPlayerWeather();
            case RAIN, SNOW -> player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
        }

        // 方块替换现在由 rules.yml 条件系统驱动，不再在此处直接触发
    }

    /** 玩家退出时同步保存该玩家数据 */
    public void savePlayer(Player player) {
        // 强制写文件（粗粒度，但玩家退出频率低可以接受）
        save();
    }
}
