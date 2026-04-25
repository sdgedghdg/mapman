package com.mapman;

import com.mapman.command.WeatherCommand;
import com.mapman.listener.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * MapMan 主插件类。
 * <p>
 * 生命周期：
 * <pre>
 * onEnable()
 *   ├─ 保存默认 config.yml
 *   ├─ 加载 Region 配置
 *   ├─ 初始化 WeatherManager（加载 data.yml）
 *   ├─ 初始化 FakeBlockCache
 *   ├─ 初始化 FakeBlockQueue（注册每 tick 运行的任务）
 *   ├─ 初始化 FakeBlockManager
 *   ├─ 预热已加载 Chunk 缓存
 *   ├─ 注册 Listener
 *   └─ 注册 Command
 *
 * onDisable()
 *   ├─ 取消队列任务
 *   ├─ 保存玩家天气数据
 *   └─ 清理所有玩家的假方块缓存
 * </pre>
 */
public final class MapMan extends JavaPlugin {

    private WeatherManager weatherManager;
    private FakeBlockCache fakeBlockCache;
    private FakeBlockQueue fakeBlockQueue;
    private FakeBlockManager fakeBlockManager;
    private Region region;
    private int viewDistance;

    // ========================================================================
    // 生命周期
    // ========================================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();

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

        // 4. 初始化各管理器
        this.weatherManager = new WeatherManager(this);
        this.fakeBlockCache = new FakeBlockCache();
        this.fakeBlockQueue = new FakeBlockQueue(this, maxPerTick);
        this.fakeBlockManager = new FakeBlockManager(this, region, targetWorld);

        // 5. 加载玩家数据
        weatherManager.load();

        // 6. 启动队列（每 tick 处理一批）
        fakeBlockQueue.runTaskTimer(this, 1L, 1L);

        // 7. 预热缓存：扫描已加载 Chunk 的表面水位置
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int count = 0;
            for (Chunk chunk : targetWorld.getLoadedChunks()) {
                fakeBlockManager.preCacheChunk(chunk);
                count++;
            }
            getLogger().info("预热完成，已扫描 " + count + " 个 Chunk，缓存 " + fakeBlockManager.cachedChunksCount() + " 个含表面水的 Chunk。");
        }, 10L);

        // 8. 定时保存玩家数据
        int saveInterval = getConfig().getInt("save-interval", 1200);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> weatherManager.save(),
                saveInterval, saveInterval);

        // 9. 注册事件和指令
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Objects.requireNonNull(getCommand("weather")).setExecutor(new WeatherCommand(this));

        getLogger().info("MapMan 已启用。");
    }

    @Override
    public void onDisable() {
        // 1. 取消队列
        if (fakeBlockQueue != null) {
            fakeBlockQueue.cancel();
            fakeBlockQueue.clear();
        }

        // 2. 保存玩家数据
        if (weatherManager != null) {
            weatherManager.save();
        }

        getLogger().info("MapMan 已禁用。");
    }

    // ========================================================================
    // 访问器
    // ========================================================================

    public WeatherManager getWeatherManager() {
        return weatherManager;
    }

    public FakeBlockCache getFakeBlockCache() {
        return fakeBlockCache;
    }

    public FakeBlockQueue getFakeBlockQueue() {
        return fakeBlockQueue;
    }

    public FakeBlockManager getFakeBlockManager() {
        return fakeBlockManager;
    }

    public Region getRegion() {
        return region;
    }

    public int getViewDistance() {
        return viewDistance;
    }
}
