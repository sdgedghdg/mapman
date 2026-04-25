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
 * 语法：/weather set <clear|rain|snow>
 * 权限：mapman.weather（默认 true）
 */
public final class WeatherCommand implements CommandExecutor {

    private final MapMan plugin;

    public WeatherCommand(MapMan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("set")) {
            player.sendMessage(Component.text("用法: /weather set <clear|rain|snow>", NamedTextColor.RED));
            return true;
        }

        WeatherType type;
        try {
            type = WeatherType.valueOf(args[1].toUpperCase());
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
}
