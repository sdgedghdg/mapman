package com.mapman.command;

import com.mapman.MapMan;
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
 *   /weather set <clear|rain|snow>  — 设置个人天气（客户端视觉 + 存储）
 *   /weather info                   — 诊断信息
 * <p>
 * 注意：方块替换行为现在由 rules.yml 的条件驱动，
 * 此命令仅控制玩家客户端的天气视觉效果和偏好存储。
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

        // 设置玩家客户端天气视觉效果
        switch (type) {
            case CLEAR -> player.resetPlayerWeather();
            case RAIN, SNOW -> player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
        }

        // 存储偏好
        plugin.getWeatherManager().setPlayerWeather(player, type);

        player.sendMessage(
                Component.text("你的个人天气已设为 ", NamedTextColor.GREEN)
                        .append(Component.text(type.name().toLowerCase(), NamedTextColor.YELLOW))
        );
        if (type == WeatherType.SNOW) {
            player.sendMessage(
                    Component.text("提示: 方块替换效果需在 rules.yml 中配置条件。", NamedTextColor.GRAY)
            );
        }
        return true;
    }

    private boolean handleInfo(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("==== MapMan 天气诊断 ====", NamedTextColor.GOLD));

        sender.sendMessage(Component.text("已加载规则数: ", NamedTextColor.AQUA)
                .append(Component.text(String.valueOf(plugin.getRuleRegistry().rules().size()), NamedTextColor.WHITE)));

        if (sender instanceof Player player) {
            WeatherType weather = plugin.getWeatherManager().getPlayerWeather(player);
            sender.sendMessage(Component.text("你的天气: ", NamedTextColor.AQUA)
                    .append(Component.text(weather.name(), NamedTextColor.WHITE)));
        }

        sender.sendMessage(Component.text("==========================", NamedTextColor.GOLD));
        return true;
    }
}
