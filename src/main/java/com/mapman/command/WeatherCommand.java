package com.mapman.command;

import com.mapman.MapMan;
import com.mapman.WeatherManager.WeatherType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WeatherCommand implements TabExecutor {

    private static final List<String> TOP_CMDS = List.of("set", "info");
    private static final List<String> WEATHER_TYPES = List.of("clear", "rain", "snow");
    private static final List<String> EMPTY = List.of();

    private final MapMan plugin;

    public WeatherCommand(MapMan plugin) {
        this.plugin = plugin;
    }

    // ========== CommandExecutor ==========

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

    // ========== TabCompleter ==========

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return prefixMatch(args[0], TOP_CMDS);
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return prefixMatch(args[1], WEATHER_TYPES);
        }
        return EMPTY;
    }

    // ========== Handlers ==========

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

        switch (type) {
            case CLEAR -> player.resetPlayerWeather();
            case RAIN, SNOW -> player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
        }

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

    // ========== Helpers ==========

    private static List<String> prefixMatch(String prefix, List<String> candidates) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(lower)) result.add(c);
        }
        return result;
    }
}
