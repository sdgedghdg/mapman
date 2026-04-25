package com.mapman;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 轴对齐包围盒（AABB），用于限定伪装生效区域。
 * 不可变，线程安全。
 */
public final class Region {

    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public Region(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Region fromConfig(ConfigurationSection section) {
        String world = section.getString("world", "world");
        int x1 = section.getInt("pos1.x", -100);
        int y1 = section.getInt("pos1.y", -64);
        int z1 = section.getInt("pos1.z", -100);
        int x2 = section.getInt("pos2.x", 100);
        int y2 = section.getInt("pos2.y", 320);
        int z2 = section.getInt("pos2.z", 100);
        return new Region(world, x1, y1, z1, x2, y2, z2);
    }

    public String worldName() {
        return worldName;
    }

    /** 判断世界坐标是否在区域内 */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    /** 判断柱状坐标 (x, z) 是否在区域的水平投影内（忽略 Y 轴） */
    public boolean containsHorizontal(int x, int z) {
        return x >= minX && x <= maxX
            && z >= minZ && z <= maxZ;
    }

    public int minY() {
        return Math.max(minY, -64);
    }

    public int maxY() {
        return Math.min(maxY, 320);
    }
}
