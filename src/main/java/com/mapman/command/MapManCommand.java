package com.mapman.command;

import com.mapman.MapMan;
import com.mapman.Region;
import com.mapman.RegionManager;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapManCommand implements TabExecutor {

    private static final List<String> TOP_CMDS = List.of("reload", "info", "apply", "undo", "rule", "region", "help");
    private static final List<String> RULE_CMDS = List.of("add", "remove", "list", "set");
    private static final List<String> REGION_CMDS = List.of("add", "remove", "list");
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
            case "reload"  -> handleReload(sender);
            case "info"    -> handleInfo(sender);
            case "apply"   -> handleApply(sender);
            case "undo"    -> handleUndo(sender);
            case "rule"    -> handleRule(sender, args);
            case "region"  -> handleRegion(sender, args);
            case "help"    -> showHelp(sender);
            default        -> showHelp(sender);
        };
    }

    // ========== TabCompleter ==========

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return prefixMatch(args[0], TOP_CMDS);

        if (args.length >= 2 && args[0].equalsIgnoreCase("rule")) {
            return tabRule(args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("region")) {
            return tabRegion(args);
        }
        return EMPTY;
    }

    private List<String> tabRule(String[] args) {
        if (args.length == 2) return prefixMatch(args[1], RULE_CMDS);
        if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
            return args.length == 3 ? prefixMatch(args[2], ruleIds()) : EMPTY;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            if (args.length == 3) return prefixMatch(args[2], ruleIds());
            String last = args[args.length - 1].toLowerCase();
            if (last.equals("--region")) return prefixMatch("", regionNames());
            String prev = args.length >= 2 ? args[args.length - 2].toLowerCase() : "";
            if (prev.equals("--region")) return prefixMatch(last, regionNames());
            return prefixMatch(last, List.of("--region", "--add-expr", "--add-perm", "--clear-conditions", "--priority"));
        }
        // rule add: suggest --expr, --perm, --region flags
        if (args.length >= 6 && args[1].equalsIgnoreCase("add")) {
            String last = args[args.length - 1].toLowerCase();
            if (last.equals("--region")) return prefixMatch("", regionNames());
            String prev = args.length >= 2 ? args[args.length - 2].toLowerCase() : "";
            if (prev.equals("--region")) return prefixMatch(last, regionNames());
            return prefixMatch(args[args.length - 1],
                    List.of("--expr", "--perm", "--region"));
        }
        return EMPTY;
    }

    private List<String> tabRegion(String[] args) {
        if (args.length == 2) return prefixMatch(args[1], REGION_CMDS);
        if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
            return args.length == 3 ? prefixMatch(args[2], regionNames()) : EMPTY;
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
        cmdLine(sender, "/mapman rule add <id> <优先级> <目标1;目标2|替换1 目标3|替换2 ...> [--expr \"...\"] [--perm \"...\"] [--region \"...\"]", "创建规则");
        cmdLine(sender, "/mapman rule set <id> --region/--add-expr/--add-perm/--clear-conditions/--priority", "修改规则");
        cmdLine(sender, "/mapman rule remove <id>", "删除规则");

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("区域", NamedTextColor.GOLD));
        cmdLine(sender, "/mapman region list",      "列出所有区域");
        cmdLine(sender, "/mapman region add <名称> <世界> <x1> <y1> <z1> <x2> <y2> <z2>", "添加区域");
        cmdLine(sender, "/mapman region remove <名称>", "删除区域");

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
        RegionManager rm = plugin.getRegionManager();
        Collection<Region> allRegions = rm.getAllRegions();
        if (allRegions.isEmpty()) {
            sender.sendMessage(Component.text("未配置任何区域。", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("==== MapMan 诊断 ====", NamedTextColor.GOLD));

        // 区域信息
        sender.sendMessage(Component.text("已配置区域 (" + allRegions.size() + "):", NamedTextColor.AQUA));
        for (Region r : allRegions) {
            sender.sendMessage(Component.text(String.format("  %s: (%d,%d,%d) → (%d,%d,%d)",
                    r.worldName(), r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()),
                    NamedTextColor.WHITE));
        }

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

            String regionName = rm.getRegionNameAt(player.getWorld().getName(),
                    player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ());
            sender.sendMessage(Component.text("所在区域: ", NamedTextColor.AQUA)
                    .append(Component.text(regionName != null ? regionName : "无", NamedTextColor.WHITE)));
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

    // ========== Region Management ==========

    private boolean handleRegion(@NotNull CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /mapman region <add|remove|list> ...", NamedTextColor.RED));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "add"    -> handleRegionAdd(sender, args);
            case "remove" -> handleRegionRemove(sender, args);
            case "list"   -> handleRegionList(sender);
            default -> {
                sender.sendMessage(Component.text("未知子命令: " + args[1] + "。可用: add, remove, list", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleRegionAdd(@NotNull CommandSender sender, String[] args) {
        if (args.length < 10) {
            sender.sendMessage(Component.text("用法: /mapman region add <名称> <世界> <x1> <y1> <z1> <x2> <y2> <z2>", NamedTextColor.RED));
            return true;
        }

        String name = args[2];
        String world = args[3];
        int x1, y1, z1, x2, y2, z2;
        try {
            x1 = Integer.parseInt(args[4]);
            y1 = Integer.parseInt(args[5]);
            z1 = Integer.parseInt(args[6]);
            x2 = Integer.parseInt(args[7]);
            y2 = Integer.parseInt(args[8]);
            z2 = Integer.parseInt(args[9]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("坐标必须为整数。", NamedTextColor.RED));
            return true;
        }

        Region region = new Region(world, x1, y1, z1, x2, y2, z2);
        plugin.getRegionManager().addRegion(name, region);
        plugin.getRegionManager().save(new File(plugin.getDataFolder(), "config.yml"));

        // 如果新增区域的 world 与 target world 相同，做一次重载让扫描器感知
        plugin.loadRules();
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.clearAllCaches();
            for (Player p : Bukkit.getOnlinePlayers()) {
                applier.undoAll(p);
                applier.applyForPlayer(p);
            }
        }

        sender.sendMessage(Component.text("区域 " + name + " 已添加: " + world
                + " (" + x1 + "," + y1 + "," + z1 + ") → (" + x2 + "," + y2 + "," + z2 + ")",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRegionRemove(@NotNull CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /mapman region remove <名称>", NamedTextColor.RED));
            return true;
        }

        String name = args[2];
        if (plugin.getRegionManager().getRegion(name) == null) {
            sender.sendMessage(Component.text("区域不存在: " + name, NamedTextColor.RED));
            return true;
        }

        plugin.getRegionManager().removeRegion(name);
        plugin.getRegionManager().save(new File(plugin.getDataFolder(), "config.yml"));

        plugin.loadRules();
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.clearAllCaches();
            for (Player p : Bukkit.getOnlinePlayers()) {
                applier.undoAll(p);
                applier.applyForPlayer(p);
            }
        }

        sender.sendMessage(Component.text("区域 " + name + " 已删除。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRegionList(@NotNull CommandSender sender) {
        RegionManager rm = plugin.getRegionManager();
        Collection<Region> all = rm.getAllRegions();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("没有已配置的区域。", NamedTextColor.GRAY));
            return true;
        }

        // 构建区域→规则的反向索引
        Map<String, List<String>> regionRules = new LinkedHashMap<>();
        for (String rn : rm.getRegionNames()) {
            regionRules.put(rn, new ArrayList<>());
        }
        for (Rule rule : plugin.getRuleRegistry().rules()) {
            if (rule.regionName() != null && regionRules.containsKey(rule.regionName())) {
                regionRules.get(rule.regionName()).add(rule.id());
            }
        }

        sender.sendMessage(Component.text("==== 区域列表 (" + all.size() + ") ====", NamedTextColor.GOLD));
        for (Region r : all) {
            // 找到区域名
            String name = rm.getRegionNameAt(r.worldName(), r.minX(), r.minY(), r.minZ());
            if (name == null) {
                for (String n : rm.getRegionNames()) {
                    if (rm.getRegion(n) == r) { name = n; break; }
                }
            }
            String displayName = name != null ? name : "?";
            sender.sendMessage(
                    Component.text("  " + displayName, NamedTextColor.AQUA)
                            .append(Component.text("  world=" + r.worldName(), NamedTextColor.GRAY))
                            .append(Component.text(String.format("  (%d,%d,%d)→(%d,%d,%d)",
                                    r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()),
                                    NamedTextColor.WHITE))
            );

            // 显示引用此区域的规则
            List<String> related = regionRules.getOrDefault(displayName, List.of());
            if (related.isEmpty()) {
                sender.sendMessage(Component.text("    引用规则: (无)", NamedTextColor.DARK_GRAY));
            } else {
                sender.sendMessage(Component.text("    引用规则: " + String.join(", ", related), NamedTextColor.GREEN));
            }
        }
        sender.sendMessage(Component.text("================================", NamedTextColor.GOLD));
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
            case "set"    -> handleRuleSet(sender, args);
            default -> {
                sender.sendMessage(Component.text("未知子命令: " + args[1] + "。可用: add, remove, list, set", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleRuleAdd(@NotNull CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(Component.text("用法: /mapman rule add <id> <优先级> <目标1;目标2|替换1 目标3|替换2 ...> [--expr \"...\"] [--perm \"...\"] [--region \"...\"]", NamedTextColor.RED));
            sender.sendMessage(Component.text("  ;  — 分隔多个目标共用一个替换", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  |  — 分隔目标组和替换方块", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  空格 — 分隔多组 target|replace 映射", NamedTextColor.GRAY));
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

        // 收集 changes 参数（从索引 4 直到遇到 --flag）
        int flagStart = args.length;
        for (int i = 4; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                flagStart = i;
                break;
            }
        }

        StringBuilder changesBuilder = new StringBuilder();
        for (int i = 4; i < flagStart; i++) {
            if (i > 4) changesBuilder.append(' ');
            changesBuilder.append(args[i]);
        }
        String changesArg = changesBuilder.toString();

        if (changesArg.isEmpty()) {
            sender.sendMessage(Component.text("changes 参数不能为空。", NamedTextColor.RED));
            return true;
        }

        // 解析 changes: "target1;target2|replace1 target3|replace2"
        Map<String, String> changes = new LinkedHashMap<>();
        for (String pair : changesArg.split(" ")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int pipeIdx = trimmed.indexOf('|');
            if (pipeIdx < 0) {
                sender.sendMessage(Component.text("格式错误: \"" + trimmed + "\" 缺少 '|'。应为 目标|替换。", NamedTextColor.RED));
                return true;
            }
            String targetsPart = trimmed.substring(0, pipeIdx);
            String replace = trimmed.substring(pipeIdx + 1);
            if (targetsPart.isEmpty() || replace.isEmpty()) {
                sender.sendMessage(Component.text("格式错误: 目标和替换不能为空。", NamedTextColor.RED));
                return true;
            }

            for (String target : targetsPart.split(";")) {
                String t = target.trim();
                if (!t.isEmpty()) {
                    changes.put(t, replace);
                }
            }
        }

        if (changes.isEmpty()) {
            sender.sendMessage(Component.text("未能解析出有效的 changes 映射。", NamedTextColor.RED));
            return true;
        }

        // 解析可选 flag
        String expr = null;
        String perm = null;
        String regionName = null;
        for (int i = flagStart; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--expr") && i + 1 < args.length) {
                expr = args[++i];
            } else if (args[i].equalsIgnoreCase("--perm") && i + 1 < args.length) {
                perm = args[++i];
            } else if (args[i].equalsIgnoreCase("--region") && i + 1 < args.length) {
                regionName = args[++i];
            }
        }

        File rulesFile = new File(plugin.getDataFolder(), "rules.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(rulesFile);
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            rulesSection = config.createSection("rules");
        }

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

        ConfigurationSection ruleSection = rulesSection.createSection(ruleId);
        ruleSection.set("priority", priority);
        if (regionName != null) {
            ruleSection.set("region", regionName);
        }
        if (!conditions.isEmpty()) {
            ruleSection.set("conditions", conditions);
        }
        ruleSection.createSection("changes", changes);

        try {
            config.save(rulesFile);
        } catch (Exception e) {
            sender.sendMessage(Component.text("写入 rules.yml 失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }

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
                + ", changes=" + changes.size() + "个映射"
                + (regionName != null ? ", region=" + regionName : "")
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
            // 条件摘要
            String condSummary = r.hasCondition() ? " [有条件]" : " [无条件]";
            String regionStr = r.regionName() != null ? " region=" + r.regionName() : " (全局)";

            sender.sendMessage(
                    Component.text("  " + r.id(), NamedTextColor.AQUA)
                            .append(Component.text("  pri=" + r.priority(), NamedTextColor.GRAY))
                            .append(Component.text(regionStr, r.regionName() != null ? NamedTextColor.YELLOW : NamedTextColor.GRAY))
                            .append(Component.text(condSummary, r.hasCondition() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
            );
            sender.sendMessage(
                    Component.text("    changes: " + r.changes(), NamedTextColor.WHITE)
            );
        }
        sender.sendMessage(Component.text("==============================", NamedTextColor.GOLD));
        return true;
    }

    private boolean handleRuleSet(@NotNull CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("用法: /mapman rule set <id> --region <name> | --add-expr \"...\" | --add-perm <perm> | --clear-conditions | --priority <n>", NamedTextColor.RED));
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

        ConfigurationSection ruleSection = rulesSection.getConfigurationSection(ruleId);
        if (ruleSection == null) {
            sender.sendMessage(Component.text("规则节点损坏: " + ruleId, NamedTextColor.RED));
            return true;
        }

        String flag = args[3].toLowerCase();
        switch (flag) {
            case "--region" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("用法: /mapman rule set <id> --region <名称>", NamedTextColor.RED));
                    return true;
                }
                ruleSection.set("region", args[4]);
                sender.sendMessage(Component.text("规则 " + ruleId + " 区域已设为 " + args[4] + "。", NamedTextColor.GREEN));
            }
            case "--add-expr" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("用法: /mapman rule set <id> --add-expr \"<表达式>\"", NamedTextColor.RED));
                    return true;
                }
                addCondition(ruleSection, "expression", Map.of("type", "expression", "expression", args[4]));
                sender.sendMessage(Component.text("规则 " + ruleId + " 已添加 expression 条件。", NamedTextColor.GREEN));
            }
            case "--add-perm" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("用法: /mapman rule set <id> --add-perm <权限>", NamedTextColor.RED));
                    return true;
                }
                addCondition(ruleSection, "permission", Map.of("type", "permission", "permission", args[4]));
                sender.sendMessage(Component.text("规则 " + ruleId + " 已添加 permission 条件。", NamedTextColor.GREEN));
            }
            case "--clear-conditions" -> {
                ruleSection.set("conditions", null);
                sender.sendMessage(Component.text("规则 " + ruleId + " 条件已清空。", NamedTextColor.GREEN));
            }
            case "--priority" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("用法: /mapman rule set <id> --priority <数值>", NamedTextColor.RED));
                    return true;
                }
                try {
                    ruleSection.set("priority", Integer.parseInt(args[4]));
                    sender.sendMessage(Component.text("规则 " + ruleId + " 优先级已设为 " + args[4] + "。", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("优先级必须为整数。", NamedTextColor.RED));
                    return true;
                }
            }
            default -> {
                sender.sendMessage(Component.text("未知标志: " + flag + "。可用: --region, --add-expr, --add-perm, --clear-conditions, --priority", NamedTextColor.RED));
                return true;
            }
        }

        try {
            config.save(rulesFile);
        } catch (Exception e) {
            sender.sendMessage(Component.text("写入 rules.yml 失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }

        plugin.loadRules();
        BlockApplier applier = plugin.getBlockApplier();
        if (applier != null) {
            applier.clearAllCaches();
            for (Player p : Bukkit.getOnlinePlayers()) {
                applier.undoAll(p);
                applier.applyForPlayer(p);
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void addCondition(ConfigurationSection ruleSection, String type, Map<String, Object> newCond) {
        List<Map<?, ?>> rawList = ruleSection.getMapList("conditions");
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (rawList != null) {
            for (Map<?, ?> raw : rawList) {
                conditions.add((Map<String, Object>) raw);
            }
        }
        conditions.add(newCond);
        ruleSection.set("conditions", conditions);
    }

    // ========== Helpers ==========

    private List<String> ruleIds() {
        List<String> ids = new ArrayList<>();
        for (Rule r : plugin.getRuleRegistry().rules()) ids.add(r.id());
        return ids;
    }

    private List<String> regionNames() {
        return new ArrayList<>(plugin.getRegionManager().getRegionNames());
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
