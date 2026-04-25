package com.mapman.command;

import com.mapman.FakeBlockManager;
import com.mapman.MapMan;
import com.mapman.Region;
import com.mapman.WeatherManager.WeatherType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /weather 指令处理器。
 * <p>
 * 语法：
 *   /weather set <clear|rain|snow>  — 设置个人天气
 *   /weather info                   — 诊断信息
 */
public final class WeatherCommand implements CommandExecutor {

    private final MapMan plugin;

    public WeatherCommand(MapMan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return handleSet(sender, args[1]);
        }

        sender.sendMessage(Component.text("用法:", NamedTextColor.RED));
        sender.sendMessage(Component.text("  /weather set <clear|rain|snow>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /weather info", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleSet(@NotNull CommandSender sender, @NotNull String typeStr) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }

        WeatherType type;
        try {
            type = WeatherType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效天气。可用: clear, rain, snow", NamedTextColor.RED));
            return true;
        }

        plugin.getWeatherManager().setPlayerWeather(player, type);

        player.sendMessage(
                Component.text("你的个人天气已设为 ", NamedTextColor.GREEN)
                        .append(Component.text(type.name().toLowerCase(), NamedTextColor.YELLOW))
        );
        return true;
    }

    private boolean handleInfo(@NotNull CommandSender sender) {
        Region region = plugin.getRegion();
        if (region == null) {
            sender.sendMessage(Component.text("区域未配置。", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("==== MapMan 诊断 ====", NamedTextColor.GOLD));

        // 区域
        sender.sendMessage(Component.text("区域世界: ", NamedTextColor.AQUA)
                .append(Component.text(region.worldName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("区域范围: ", NamedTextColor.AQUA)
                .append(Component.text(String.format("(%d,%d,%d) → (%d,%d,%d)",
                        region.minX(), region.minY(), region.minZ(),
                        region.maxX(), region.maxY(), region.maxZ()), NamedTextColor.WHITE)));

        // 缓存统计
        FakeBlockManager fbm = plugin.getFakeBlockManager();
        sender.sendMessage(Component.text("已缓存 Chunk 数: ", NamedTextColor.AQUA)
                .append(Component.text(String.valueOf(fbm.cachedChunksCount()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("表面水坐标总数: ", NamedTextColor.AQUA)
                .append(Component.text(String.valueOf(fbm.totalWaterPositions()), NamedTextColor.WHITE)));

        // 玩家信息
        if (sender instanceof Player player) {
            WeatherType weather = plugin.getWeatherManager().getPlayerWeather(player);
            sender.sendMessage(Component.text("你的天气: ", NamedTextColor.AQUA)
                    .append(Component.text(weather.name(), NamedTextColor.WHITE)));

            sender.sendMessage(Component.text("所在世界: ", NamedTextColor.AQUA)
                    .append(Component.text(player.getWorld().getName(), NamedTextColor.WHITE)));

            // 玩家所在 Chunk 信息
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            sender.sendMessage(Component.text("所在 Chunk: ", NamedTextColor.AQUA)
                    .append(Component.text(String.format("(%d, %d)", cx, cz), NamedTextColor.WHITE)));

            sender.sendMessage(Component.text("视距: ", NamedTextColor.AQUA)
                    .append(Component.text(String.valueOf(plugin.getViewDistance()) + " Chunk", NamedTextColor.WHITE)));

            // 匹配世界
            boolean worldMatch = player.getWorld().getName().equals(region.worldName());
            sender.sendMessage(Component.text("世界匹配区域: ", NamedTextColor.AQUA)
                    .append(Component.text(worldMatch ? "是" : "否",
                            worldMatch ? NamedTextColor.GREEN : NamedTextColor.RED)));

            // 排队中的任务
            sender.sendMessage(Component.text("队列待处理: ", NamedTextColor.AQUA)
                    .append(Component.text(String.valueOf(plugin.getFakeBlockQueue().pending()), NamedTextColor.WHITE)));
        }

        sender.sendMessage(Component.text("====================", NamedTextColor.GOLD));
        return true;
    }
}
