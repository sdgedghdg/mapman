package com.mapman;

/**
 * 不可变的方块坐标三元组。
 * 相比 {@link org.bukkit.Location} 轻量且不持有 World 引用。
 *
 * @param x 世界 X 坐标
 * @param y 世界 Y 坐标
 * @param z 世界 Z 坐标
 */
public record BlockPosition(int x, int y, int z) {}
