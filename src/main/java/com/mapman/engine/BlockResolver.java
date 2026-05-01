package com.mapman.engine;

import com.mapman.MapMan;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.CustomBlock;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方块标识符解析器。
 * 将 "minecraft:water" 或 "minecraft:sea_pickle[waterlogged=true]" 解析为 BlockData。
 * CE 不可用时，自定义方块 ID 统一返回 null。
 */
public final class BlockResolver {

    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final Pattern STATE_PATTERN = Pattern.compile("^(.+)\\[(.+)\\]$");

    private BlockResolver() {}

    /**
     * 解析方块标识符为 BlockData。支持方块状态语法。
     */
    @Nullable
    public static BlockData resolve(String id) {
        if (id == null || id.isEmpty()) return null;

        String baseId = id;
        Map<String, String> properties = null;
        Matcher m = STATE_PATTERN.matcher(id);
        if (m.matches()) {
            baseId = m.group(1);
            properties = parseProperties(m.group(2));
        }

        int colon = baseId.indexOf(':');
        String namespace = (colon > 0) ? baseId.substring(0, colon) : VANILLA_NAMESPACE;
        String path = (colon > 0) ? baseId.substring(colon + 1) : baseId;

        if (namespace.equals(VANILLA_NAMESPACE)) {
            return resolveVanilla(path, properties);
        } else {
            return resolveCustom(namespace, path);
        }
    }

    /** 获取不含方块状态的基 ID */
    public static String baseId(String id) {
        if (id == null) return null;
        Matcher m = STATE_PATTERN.matcher(id);
        return m.matches() ? m.group(1) : id;
    }

    /** 检查 ID 是否包含方块状态 */
    public static boolean hasState(String id) {
        return id != null && STATE_PATTERN.matcher(id).matches();
    }

    /**
     * 解析目标方块标识符，返回用于扫描检测的 Material。
     */
    @Nullable
    public static Material resolveTargetMaterial(String id) {
        if (id == null || id.isEmpty()) return null;

        String base = baseId(id);
        int colon = base.indexOf(':');
        String namespace = (colon > 0) ? base.substring(0, colon) : VANILLA_NAMESPACE;
        String path = (colon > 0) ? base.substring(colon + 1) : base;

        if (namespace.equals(VANILLA_NAMESPACE)) {
            return Material.matchMaterial(path, false);
        } else if (MapMan.hasCraftEngine()) {
            Key key = Key.from(base);
            CustomBlock block = CraftEngineBlocks.byId(key);
            if (block == null) return null;
            BlockData visualData = resolveCustom(namespace, path);
            if (visualData == null) return null;
            return visualData.getMaterial();
        }
        return null;
    }

    /** 检查标识符是否指向 CraftEngine 自定义方块。 */
    public static boolean isCustomBlockId(String id) {
        if (!MapMan.hasCraftEngine() || id == null || id.isEmpty()) return false;
        String base = baseId(id);
        int colon = base.indexOf(':');
        if (colon <= 0) return false;
        return !base.substring(0, colon).equals(VANILLA_NAMESPACE);
    }

    // ========== 内部解析 ==========

    @Nullable
    private static BlockData resolveVanilla(String path, @Nullable Map<String, String> properties) {
        Material material = Material.matchMaterial(path, false);
        if (material == null) {
            material = Material.matchMaterial(path.toUpperCase(), false);
        }
        if (material == null) return null;

        if (properties != null && !properties.isEmpty()) {
            StringBuilder sb = new StringBuilder("minecraft:").append(path).append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : properties.entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
            try {
                return material.createBlockData(sb.toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return material.createBlockData();
    }

    @Nullable
    private static BlockData resolveCustom(String namespace, String path) {
        if (!MapMan.hasCraftEngine()) return null;
        try {
            Key key = Key.from(namespace + ":" + path);
            CustomBlock block = CraftEngineBlocks.byId(key);
            if (block == null) return null;
            Object nmsState = block.defaultState().visualBlockState().literalObject();
            return BlockStateUtils.fromBlockData(nmsState);
        } catch (Exception e) {
            return null;
        }
    }

    /** "waterlogged=true,pickle=3" → Map */
    private static Map<String, String> parseProperties(String props) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : props.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return map;
    }
}
