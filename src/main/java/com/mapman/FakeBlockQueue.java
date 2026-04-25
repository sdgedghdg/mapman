package com.mapman;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 假方块发送队列。
 * <p>
 * 将需要发送的 BlockChange 放入队列，每 tick 固定处理 N 个，
 * 避免单帧网络包过多导致客户端卡顿。
 * 继承 BukkitRunnable，启动后每 tick 执行 processTick()。
 */
public final class FakeBlockQueue extends BukkitRunnable {

    private final MapMan plugin;
    private final Queue<Task> queue = new ConcurrentLinkedDeque<>();
    private int maxPerTick;

    public FakeBlockQueue(MapMan plugin, int maxPerTick) {
        this.plugin = plugin;
        this.maxPerTick = maxPerTick;
    }

    /** 动态调整每 tick 上限 */
    public void setMaxPerTick(int maxPerTick) {
        this.maxPerTick = Math.max(1, maxPerTick);
    }

    /** 入队一个方块变化任务 */
    public void enqueue(UUID playerId, Location location, BlockData data) {
        queue.add(new Task(playerId, location, data));
    }

    @Override
    public void run() {
        processTick();
    }

    /** 处理一批任务，返回本次处理数量（用于日志/调试） */
    public int processTick() {
        int processed = 0;
        for (int i = 0; i < maxPerTick; i++) {
            Task task = queue.poll();
            if (task == null) break;

            Player player = Bukkit.getPlayer(task.playerId());
            if (player != null && player.isOnline()) {
                player.sendBlockChange(task.location(), task.data());
                processed++;
            }
        }
        return processed;
    }

    /** 清空未处理的任务 */
    public void clear() {
        queue.clear();
    }

    /** 队列中待处理的任务数 */
    public int pending() {
        return queue.size();
    }

    private record Task(UUID playerId, Location location, BlockData data) {}
}
