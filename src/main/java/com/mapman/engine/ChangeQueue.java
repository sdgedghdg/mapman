package com.mapman.engine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 方块变化发送队列。
 * 每 tick 处理最多 maxPerTick 个 sendBlockChange。
 */
public final class ChangeQueue extends BukkitRunnable {

    private final Queue<Task> queue = new ConcurrentLinkedDeque<>();
    private final JavaPlugin plugin;
    private int maxPerTick;

    public ChangeQueue(JavaPlugin plugin, int maxPerTick) {
        this.plugin = plugin;
        this.maxPerTick = Math.max(1, maxPerTick);
    }

    public void setMaxPerTick(int maxPerTick) {
        this.maxPerTick = Math.max(1, maxPerTick);
    }

    public void enqueue(UUID playerId, Location location, BlockData data) {
        queue.add(new Task(playerId, location, data));
    }

    @Override
    public void run() {
        processTick();
    }

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

    public void clear() {
        queue.clear();
    }

    public int pending() {
        return queue.size();
    }

    private record Task(UUID playerId, Location location, BlockData data) {}
}
