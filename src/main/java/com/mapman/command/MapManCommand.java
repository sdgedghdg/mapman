package com.mapman.command;

import com.mapman.MapMan;
import com.mapman.Region;
import com.mapman.engine.BlockApplier;
import com.mapman.engine.ChunkScanner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /mapman 指令处理器。
 *
 * 语法：
 *   /mapman reload      — 重载配置和规则
 *   /mapman info        — 诊断信息
 *   /mapman apply       — 强制重新应用规则（调试用）
 *   /mapman undo        — 撤销所有假方块（调试用）
 */
public final class MapManCommand implements CommandExecutor {

    private final MapMan plugin;

    public MapManCommand(MapMan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("用法: /mapman <reload|info|apply|undo>", NamedTextColor.RED));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            case "apply" -> handleApply(sender);
            case "undo" -> handleUndo(sender);
            default -> {
                sender.sendMessage(Component.text("未知子指令: " + args[0], NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleReload(@NotNull CommandSender sender) {
        plugin.reloadConfig();
        plugin.loadRules();
        sender.sendMessage(Component.text("配置已重载。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleInfo(@NotNull CommandSender sender) {
        Region region = plugin.getRegion();
        if (region == null) {
            sender.sendMessage(Component.text("区域未配置。", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("==== MapMan 诊断 ====", NamedTextColor.GOLD));

        sender.sendMessage(Component.text("区域世界: ", NamedTextColor.AQUA)
                .append(Component.text(region.worldName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("区域范围: ", NamedTextColor.AQUA)
                .append(Component.text(String.format("(%d,%d,%d) → (%d,%d,%d)",
                        region.minX(), region.minY(), region.minZ(),
                        region.maxX(), region.maxY(), region.maxZ()), NamedTextColor.WHITE)));

        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            sender.sendMessage(Component.text("已缓存 Chunk 数: ", NamedTextColor.AQUA)
                    .append(Component.text(String.valueOf(applier.chunkScanner().cachedChunksCount()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("目标方块坐标总数: ", NamedTextColor.AQUA)
                    .append(Component.text(String.valueOf(applier.chunkScanner().totalPositionsCount()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("队列待处理: ", NamedTextColor.AQUA)
                    .append(Component.text(String.valueOf(applier.changeQueue().pending()), NamedTextColor.WHITE)));
        }

        sender.sendMessage(Component.text("已加载规则数: ", NamedTextColor.AQUA)
                .append(Component.text(String.valueOf(plugin.getRuleRegistry().rules().size()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("目标 Material 集合: ", NamedTextColor.AQUA)
                .append(Component.text(plugin.getRuleRegistry().targetMaterials().toString(), NamedTextColor.WHITE)));

        if (sender instanceof Player player) {
            sender.sendMessage(Component.text("所在世界: ", NamedTextColor.AQUA)
                    .append(Component.text(player.getWorld().getName(), NamedTextColor.WHITE)));
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            sender.sendMessage(Component.text("所在 Chunk: ", NamedTextColor.AQUA)
                    .append(Component.text(String.format("(%d, %d)", cx, cz), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("视距: ", NamedTextColor.AQUA)
                    .append(Component.text(String.valueOf(plugin.getViewDistance()) + " Chunk", NamedTextColor.WHITE)));

            boolean worldMatch = player.getWorld().getName().equals(region.worldName());
            sender.sendMessage(Component.text("世界匹配区域: ", NamedTextColor.AQUA)
                    .append(Component.text(worldMatch ? "是" : "否",
                            worldMatch ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }

        sender.sendMessage(Component.text("====================", NamedTextColor.GOLD));
        return true;
    }

    private boolean handleApply(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.applyForPlayer(player);
            sender.sendMessage(Component.text("已强制应用规则。", NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleUndo(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.undoAll(player);
            sender.sendMessage(Component.text("已撤销所有假方块。", NamedTextColor.GREEN));
        }
        return true;
    }
}
