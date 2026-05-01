package com.mapman;

import com.mapman.command.MapManCommand;
import com.mapman.command.WeatherCommand;
import com.mapman.engine.BlockApplier;
import com.mapman.engine.ChangeQueue;
import com.mapman.engine.RuleRegistry;
import com.mapman.listener.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

/**
 * MapMan — 条件驱动客户端方块替换插件。
 *
 * 生命周期：
 * onEnable()
 *   ├─ 保存默认 config.yml
 *   ├─ 加载 rules.yml
 *   ├─ 加载 Region 配置
 *   ├─ 初始化 WeatherManager（加载 data.yml）
 *   ├─ 初始化 RuleRegistry（编译条件）
 *   ├─ 初始化 BlockApplier + ChangeQueue
 *   ├─ 预热已加载 Chunk 缓存
 *   ├─ 启动 ChangeQueue 定时任务
 *   ├─ 启动条件重评估定时任务
 *   ├─ 注册 Listener
 *   └─ 注册 Command
 */
public final class MapMan extends JavaPlugin {

    private static boolean craftEngineAvailable = false;

    private WeatherManager weatherManager;
    private RuleRegistry ruleRegistry;
    private BlockApplier blockApplier;
    private Region region;
    private int viewDistance;

    // ========================================================================
    // 生命周期
    // ========================================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 0. 检测 CraftEngine 是否可用
        craftEngineAvailable = Bukkit.getPluginManager().getPlugin("CraftEngine") != null;
        if (craftEngineAvailable) {
            getLogger().info("检测到 CraftEngine，启用完整条件系统与 CE 方块支持。");
        } else {
            getLogger().warning("未检测到 CraftEngine。条件求值系统将不可用，所有条件视为恒真。");
            getLogger().warning("CE 自定义方块目标也无法解析。插件仅支持原版方块替换。");
        }

        // 1. 加载区域
        this.region = Region.fromConfig(getConfig().getConfigurationSection("region"));

        // 2. 获取目标世界
        String worldName = region.worldName();
        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            getLogger().severe("目标世界 \"" + worldName + "\" 不存在！插件将禁用。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. 性能设置
        this.viewDistance = getConfig().getInt("performance.view-distance", 3);
        int maxPerTick = getConfig().getInt("performance.max-per-tick", 200);
        int reEvalInterval = getConfig().getInt("performance.re-eval-interval", 100); // ticks

        // 4. 加载规则
        this.ruleRegistry = new RuleRegistry();
        loadRules();

        // 5. 初始化各管理器
        this.weatherManager = new WeatherManager(this);
        this.blockApplier = new BlockApplier(this, ruleRegistry, region, targetWorld, viewDistance, maxPerTick);

        // 6. 加载玩家数据
        weatherManager.load();

        // 7. 启动队列（每 tick 处理一批）
        blockApplier.changeQueue().runTaskTimer(this, 1L, 1L);

        // 8. 预热缓存
        Bukkit.getScheduler().runTaskLater(this, () -> {
            blockApplier.preWarm();
        }, 10L);

        // 9. 定时保存玩家数据
        int saveInterval = getConfig().getInt("save-interval", 1200);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> weatherManager.save(),
                saveInterval, saveInterval);

        // 10. 定时重新评估条件
        blockApplier.startReEvalTimer(reEvalInterval);

        // 11. 注册事件和指令
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        MapManCommand mapManCmd = new MapManCommand(this);
        Objects.requireNonNull(getCommand("mapman")).setExecutor(mapManCmd);
        Objects.requireNonNull(getCommand("mapman")).setTabCompleter(mapManCmd);

        WeatherCommand weatherCmd = new WeatherCommand(this);
        Objects.requireNonNull(getCommand("weather")).setExecutor(weatherCmd);
        Objects.requireNonNull(getCommand("weather")).setTabCompleter(weatherCmd);

        getLogger().info("MapMan 已启用。");
    }

    @Override
    public void onDisable() {
        // 1. 取消队列
        if (blockApplier != null) {
            blockApplier.changeQueue().cancel();
            blockApplier.changeQueue().clear();
        }

        // 2. 保存玩家数据
        if (weatherManager != null) {
            weatherManager.save();
        }

        getLogger().info("MapMan 已禁用。");
    }

    // ========================================================================
    // 规则加载
    // ========================================================================

    /** 加载 rules.yml */
    public void loadRules() {
        File rulesFile = new File(getDataFolder(), "rules.yml");
        if (!rulesFile.exists()) {
            saveResource("rules.yml", false);
        }
        ruleRegistry.load(rulesFile);
        getLogger().info("已加载 " + ruleRegistry.rules().size() + " 条规则。");
    }

    /** 重新加载区域配置 */
    public void reloadRegion() {
        reloadConfig();
        this.region = Region.fromConfig(getConfig().getConfigurationSection("region"));
    }

    // ========================================================================
    // 访问器 / 工具
    // ========================================================================

    /** CraftEngine 是否已加载 */
    public static boolean hasCraftEngine() { return craftEngineAvailable; }

    public WeatherManager getWeatherManager() { return weatherManager; }
    public RuleRegistry getRuleRegistry() { return ruleRegistry; }
    public BlockApplier getBlockApplier() { return blockApplier; }
    public Region getRegion() { return region; }
    public int getViewDistance() { return viewDistance; }

    /** @deprecated 旧版 API 兼容，返回 blockApplier 的 changeQueue */
    @Deprecated
    public ChangeQueue getFakeBlockQueue() { return blockApplier != null ? blockApplier.changeQueue() : null; }
}
