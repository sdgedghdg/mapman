package com.mapman.engine;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.CustomBlock;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

/**
 * 方块标识符解析器。
 * 将 "minecraft:water" 或 "default:topaz_ore" 等字符串解析为 BlockData。
 */
public final class BlockResolver {

    private static final String VANILLA_NAMESPACE = "minecraft";

    private BlockResolver() {}

    /**
     * 解析方块标识符为 BlockData。
     *
     * @param id 形如 "minecraft:packed_ice" 或 "default:topaz_ore"
     * @return BlockData，解析失败返回 null
     */
    @Nullable
    public static BlockData resolve(String id) {
        if (id == null || id.isEmpty()) return null;

        int colon = id.indexOf(':');
        String namespace = (colon > 0) ? id.substring(0, colon) : VANILLA_NAMESPACE;
        String path = (colon > 0) ? id.substring(colon + 1) : id;

        if (namespace.equals(VANILLA_NAMESPACE)) {
            return resolveVanilla(path);
        } else {
            return resolveCustom(namespace, path);
        }
    }

    /**
     * 解析目标方块标识符，返回用于扫描检测的 Material。
     * CE 自定义方块会被映射到其底层 vanilla material。
     *
     * @param id 方块标识符
     * @return Material，解析失败返回 null
     */
    @Nullable
    public static Material resolveTargetMaterial(String id) {
        if (id == null || id.isEmpty()) return null;

        int colon = id.indexOf(':');
        String namespace = (colon > 0) ? id.substring(0, colon) : VANILLA_NAMESPACE;
        String path = (colon > 0) ? id.substring(colon + 1) : id;

        if (namespace.equals(VANILLA_NAMESPACE)) {
            return Material.matchMaterial(path, false);
        } else {
            // CE 自定义方块: 找到其默认状态的视觉 BlockData 再取 Material
            Key key = Key.from(id);
            CustomBlock block = CraftEngineBlocks.byId(key);
            if (block == null) return null;
            BlockData visualData = resolveCustom(namespace, path);
            if (visualData == null) return null;
            return visualData.getMaterial();
        }
    }

    /**
     * 检查标识符是否指向 CraftEngine 自定义方块。
     */
    public static boolean isCustomBlockId(String id) {
        if (id == null || id.isEmpty()) return false;
        int colon = id.indexOf(':');
        if (colon <= 0) return false; // 没有 namespace 或 minecraft:xxx
        String namespace = id.substring(0, colon);
        return !namespace.equals(VANILLA_NAMESPACE);
    }

    // ========== 内部解析 ==========

    @Nullable
    private static BlockData resolveVanilla(String path) {
        Material material = Material.matchMaterial(path, false);
        if (material == null) {
            // 尝试全大写
            material = Material.matchMaterial(path.toUpperCase(), false);
        }
        if (material == null) return null;
        return material.createBlockData();
    }

    @Nullable
    private static BlockData resolveCustom(String namespace, String path) {
        try {
            Key key = Key.from(namespace + ":" + path);
            CustomBlock block = CraftEngineBlocks.byId(key);
            if (block == null) return null;
            // 取默认状态的 visualBlockState 转为 Bukkit BlockData
            Object nmsState = block.defaultState().visualBlockState().literalObject();
            return BlockStateUtils.fromBlockData(nmsState);
        } catch (Exception e) {
            return null;
        }
    }
}
