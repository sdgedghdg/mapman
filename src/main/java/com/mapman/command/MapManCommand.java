package com.mapman.command;

import com.mapman.MapMan;
import com.mapman.Region;
import com.mapman.engine.BlockApplier;
import com.mapman.engine.Rule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapManCommand implements TabExecutor {

    private static final List<String> TOP_CMDS = List.of("reload", "info", "apply", "undo", "rule", "help");
    private static final List<String> RULE_CMDS = List.of("add", "remove", "list");
    private static final List<String> EMPTY = List.of();

    private final MapMan plugin;

    public MapManCommand(MapMan plugin) {
        this.plugin = plugin;
    }

    // ========== CommandExecutor ==========

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return showHelp(sender);

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "info"   -> handleInfo(sender);
            case "apply"  -> handleApply(sender);
            case "undo"   -> handleUndo(sender);
            case "rule"   -> handleRule(sender, args);
            case "help"   -> showHelp(sender);
            default       -> showHelp(sender);
        };
    }

    // ========== TabCompleter ==========

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return TOP_CMDS;
        if (args.length == 1) return prefixMatch(args[0], TOP_CMDS);
        if (args.length >= 2 && args[0].equalsIgnoreCase("rule")) {
            return switch (args[1].toLowerCase()) {
                case "add", "remove" -> args.length == 2 ? RULE_CMDS
                        : args.length == 3 && args[1].equalsIgnoreCase("remove")
                            ? prefixMatch(args[2], ruleIds())
                            : EMPTY;
                case "list" -> EMPTY;
                default -> prefixMatch(args[1], RULE_CMDS);
            };
        }
        return EMPTY;
    }

    // ========== Help ==========

    private boolean showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("▸ MapMan 帮助 ◂", NamedTextColor.GOLD, TextDecoration.BOLD));

        sender.sendMessage(Component.text("管理", NamedTextColor.GOLD));
        cmdLine(sender, "/mapman reload",          "重载配置和规则");
        cmdLine(sender, "/mapman info",            "查看诊断信息");
        cmdLine(sender, "/mapman apply",           "强制重新应用规则 (玩家)");
        cmdLine(sender, "/mapman undo",            "撤销所有假方块 (玩家)");

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("规则", NamedTextColor.GOLD));
        cmdLine(sender, "/mapman rule list",       "列出所有规则");
        cmdLine(sender, "/mapman rule add <id> <优先级> <目标方块> <替换方块> [--expr \"...\"] [--perm \"...\"]", "创建规则");
        cmdLine(sender, "/mapman rule remove <id>", "删除规则");

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("天气", NamedTextColor.GOLD));
        cmdLine(sender, "/weather set <clear|rain|snow>", "设置个人天气视觉");
        cmdLine(sender, "/weather info",            "查看天气状态");

        sender.sendMessage(Component.empty());
        cmdLine(sender, "/mapman help",             "显示此帮助");
        return true;
    }

    private static void cmdLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(
                Component.text("  ").append(Component.text(cmd, NamedTextColor.AQUA))
                        .append(Component.text(" — " + desc, NamedTextColor.GRAY))
        );
    }

    // ========== Reload / Info / Apply / Undo ==========

    private boolean handleReload(@NotNull CommandSender sender) {
        plugin.reloadRegion();
        plugin.loadRules();
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.clearAllCaches();
            for (Player p : Bukkit.getOnlinePlayers()) {
                applier.undoAll(p);
                applier.applyForPlayer(p);
            }
        }
        sender.sendMessage(Component.text("配置已重载，缓存已清空，所有玩家已重新应用。", NamedTextColor.GREEN));
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
        Set<String> customIds = plugin.getRuleRegistry().targetCustomIds();
        if (!customIds.isEmpty()) {
            sender.sendMessage(Component.text("目标 CE 方块: ", NamedTextColor.AQUA)
                    .append(Component.text(customIds.toString(), NamedTextColor.WHITE)));
        }

        if (sender instanceof Player player) {
            sender.sendMessage(Component.text("所在世界: ", NamedTextColor.AQUA)
                    .append(Component.text(player.getWorld().getName(), NamedTextColor.WHITE)));
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            sender.sendMessage(Component.text("所在 Chunk: ", NamedTextColor.AQUA)
                    .append(Component.text(String.format("(%d, %d)", cx, cz), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("视距: ", NamedTextColor.AQUA)
                    .append(Component.text(plugin.getViewDistance() + " Chunk", NamedTextColor.WHITE)));
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
            applier.undoAll(player);
            applier.applyForPlayer(player);
            sender.sendMessage(Component.text("已撤销并重新应用规则。", NamedTextColor.GREEN));
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

    // ========== Rule Management ==========

    private boolean handleRule(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /mapman rule <add|remove|list> ...", NamedTextColor.RED));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "add"    -> handleRuleAdd(sender, args);
            case "remove" -> handleRuleRemove(sender, args);
            case "list"   -> handleRuleList(sender);
            default -> {
                sender.sendMessage(Component.text("未知子命令: " + args[1] + "。可用: add, remove, list", NamedTextColor.RED));
                yield true;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private boolean handleRuleAdd(@NotNull CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(Component.text("用法: /mapman rule add <id> <优先级> <目标方块> <替换方块> [--expr \"...\"] [--perm \"...\"]", NamedTextColor.RED));
            return true;
        }

        String ruleId = args[2];
        int priority;
        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("优先级必须为整数。", NamedTextColor.RED));
            return true;
        }

        String targetBlock = args[4];
        String replaceBlock = args[5];

        // 解析可选条件 flag
        String expr = null;
        String perm = null;
        for (int i = 6; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--expr") && i + 1 < args.length) {
                expr = args[++i];
            } else if (args[i].equalsIgnoreCase("--perm") && i + 1 < args.length) {
                perm = args[++i];
            }
        }

        // 读取 rules.yml
        File rulesFile = new File(plugin.getDataFolder(), "rules.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(rulesFile);
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            rulesSection = config.createSection("rules");
        }

        // 构建条件列表
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (expr != null) {
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("type", "expression");
            cond.put("expression", expr);
            conditions.add(cond);
        }
        if (perm != null) {
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("type", "permission");
            cond.put("permission", perm);
            conditions.add(cond);
        }

        // 写入
        ConfigurationSection ruleSection = rulesSection.createSection(ruleId);
        ruleSection.set("priority", priority);
        if (!conditions.isEmpty()) {
            ruleSection.set("conditions", conditions);
        }
        ruleSection.createSection("changes", Map.of(targetBlock, replaceBlock));

        try {
            config.save(rulesFile);
        } catch (Exception e) {
            sender.sendMessage(Component.text("写入 rules.yml 失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }

        // 重载
        plugin.loadRules();
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.clearAllCaches();
            for (Player p : Bukkit.getOnlinePlayers()) {
                applier.undoAll(p);
                applier.applyForPlayer(p);
            }
        }

        sender.sendMessage(Component.text("规则 " + ruleId + " 已创建并生效。priority=" + priority
                + ", target=" + targetBlock + " → " + replaceBlock
                + (expr != null ? ", expr=" + expr : "")
                + (perm != null ? ", perm=" + perm : ""), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRuleRemove(@NotNull CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /mapman rule remove <id>", NamedTextColor.RED));
            return true;
        }

        String ruleId = args[2];
        File rulesFile = new File(plugin.getDataFolder(), "rules.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(rulesFile);
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null || !rulesSection.contains(ruleId)) {
            sender.sendMessage(Component.text("规则不存在: " + ruleId, NamedTextColor.RED));
            return true;
        }

        rulesSection.set(ruleId, null);

        try {
            config.save(rulesFile);
        } catch (Exception e) {
            sender.sendMessage(Component.text("写入 rules.yml 失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }

        // 重载
        plugin.loadRules();
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.clearAllCaches();
            for (Player p : Bukkit.getOnlinePlayers()) {
                applier.undoAll(p);
                applier.applyForPlayer(p);
            }
        }

        sender.sendMessage(Component.text("规则 " + ruleId + " 已删除并生效。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRuleList(@NotNull CommandSender sender) {
        List<Rule> rules = plugin.getRuleRegistry().rules();
        if (rules.isEmpty()) {
            sender.sendMessage(Component.text("没有已加载的规则。", NamedTextColor.GRAY));
            return true;
        }

        sender.sendMessage(Component.text("==== 规则列表 (" + rules.size() + ") ====", NamedTextColor.GOLD));
        for (Rule r : rules) {
            sender.sendMessage(
                    Component.text("  " + r.id(), NamedTextColor.AQUA)
                            .append(Component.text("  pri=" + r.priority(), NamedTextColor.GRAY))
                            .append(Component.text("  changes=" + r.changes(), NamedTextColor.WHITE))
            );
        }
        sender.sendMessage(Component.text("=============================", NamedTextColor.GOLD));
        return true;
    }

    // ========== Helpers ==========

    private List<String> ruleIds() {
        List<String> ids = new ArrayList<>();
        for (Rule r : plugin.getRuleRegistry().rules()) {
            ids.add(r.id());
        }
        return ids;
    }

    private static List<String> prefixMatch(String prefix, List<String> candidates) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(lower)) result.add(c);
        }
        return result;
    }
}
