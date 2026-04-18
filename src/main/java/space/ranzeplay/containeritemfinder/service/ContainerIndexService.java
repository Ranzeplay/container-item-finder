package space.ranzeplay.containeritemfinder.service;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ContainerIndexService {
    private static final Map<UUID, SearchTask> activeTasks = new ConcurrentHashMap<>();

    public record IndexedItem(String itemName, String id, int count, BlockPos containerPos) {
    }

    public static List<IndexedItem> indexItemsInContainer(BlockEntity container, BlockPos pos) {
        List<IndexedItem> items = new ArrayList<>();

        if (container instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (!stack.isEmpty()) {
                    items.add(new IndexedItem(
                            stack.getItem().getName(stack).getString(),
                            stack.getItem().getDescriptionId(),
                            stack.getCount(),
                            pos
                    ));
                }
            }
        } else if (container instanceof ShulkerBoxBlockEntity shulker) {
            for (int i = 0; i < shulker.getContainerSize(); i++) {
                ItemStack stack = shulker.getItem(i);
                if (!stack.isEmpty()) {
                    items.add(new IndexedItem(
                            stack.getItem().getName(stack).getString(),
                            stack.getItem().getDescriptionId(),
                            stack.getCount(),
                            pos
                    ));
                }
            }
        }

        return items;
    }

    private static List<IndexedItem> indexContainersInRange(SearchTask task, Level level, BlockPos center, int range) {
        List<IndexedItem> allItems = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        int totalContainersSearched = 0;

        // Start with center position
        queue.offer(center);
        visited.add(center);

        // BFS directions (6 directions: up, down, north, south, east, west)
        int[][] directions = {
                {0, 1, 0},  // up
                {0, -1, 0}, // down
                {0, 0, -1}, // north
                {0, 0, 1},  // south
                {1, 0, 0},  // east
                {-1, 0, 0}  // west
        };

        int currentDistance = 0;
        int nodesAtCurrentDistance = 1;
        int nodesAtNextDistance = 0;

        while (!queue.isEmpty() && !task.isCancelled()) {
            BlockPos current = queue.poll();
            assert current != null;
            nodesAtCurrentDistance--;

            // Check if current position has a container
            BlockEntity blockEntity = level.getChunk(current).getBlockEntity(current);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                totalContainersSearched++;
                allItems.addAll(indexItemsInContainer(blockEntity, current));

                // Send message when a container is indexed
                if (task.source != null) {
                    task.source.sendSystemMessage(task.createIndexedContainerMessage(current));
                }
            }

            // If we haven't reached max range, explore neighbors
            if (currentDistance < range) {
                for (int[] dir : directions) {
                    BlockPos nextPos = new BlockPos(
                            current.getX() + dir[0],
                            current.getY() + dir[1],
                            current.getZ() + dir[2]
                    );

                    if (!visited.contains(nextPos)) {
                        visited.add(nextPos);
                        queue.offer(nextPos);
                        nodesAtNextDistance++;
                    }
                }
            }

            if (nodesAtCurrentDistance == 0) {
                currentDistance++;
                nodesAtCurrentDistance = nodesAtNextDistance;
                nodesAtNextDistance = 0;
            }

            // Update blocks searched count and send heartbeat
            task.blocksSearched.incrementAndGet();
            double distance = Math.sqrt(
                    Math.pow(current.getX() - center.getX(), 2) + Math.pow(current.getY() - center.getY(), 2) + Math.pow(current.getZ() - center.getZ(), 2)
            );
            task.sendHeartbeat(distance);
        }

        task.totalContainersSearched = totalContainersSearched;
        return allItems;
    }

    private static Component createIndexResultMessage(List<IndexedItem> items, int totalContainersSearched) {
        if (items.isEmpty()) {
            return Component.translatable("info.cif.instant.index.not_found")
                    .withStyle(ChatFormatting.RED);
        }

        MutableComponent message = Component.empty();

        // First line: Summary
        message.append(Component.translatable("info.cif.instant.index.summary_1", items.size(), totalContainersSearched)
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\n"));

        // Group items by name and count total
        Map<String, Integer> itemTotals = new HashMap<>();
        Map<String, List<BlockPos>> itemLocations = new HashMap<>();

        for (IndexedItem item : items) {
            itemTotals.merge(item.itemName(), item.count(), Integer::sum);
            itemLocations.computeIfAbsent(item.itemName(), k -> new ArrayList<>())
                    .add(item.containerPos());
        }

        // Sort items by total count
        List<Map.Entry<String, Integer>> sortedItems = new ArrayList<>(itemTotals.entrySet());
        sortedItems.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        // Add each item's information
        for (Map.Entry<String, Integer> entry : sortedItems) {
            String itemName = entry.getKey();
            int totalCount = entry.getValue();
            List<BlockPos> locations = itemLocations.get(itemName);

            message.append(Component.translatable("info.cif.instant.index.item_partial",
                            totalCount, itemName, locations.size())
                    .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n"));
        }

        return message;
    }

    public Component indexContainers(CommandSourceStack source, Level level, Vec3 center, int range) {
        if (!source.isPlayer()) {
            return Component.translatable("info.cif.player_only").withStyle(ChatFormatting.RED);
        }

        var player = source.getPlayer();
        assert player != null;

        UUID playerId = player.getUUID();
        if (activeTasks.containsKey(playerId)) {
            return Component.translatable("info.cif.instant.task_wip").withStyle(ChatFormatting.RED);
        }

        SearchTask task = new SearchTask(player, level, center, range);
        activeTasks.put(playerId, task);

        try {
            BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);
            List<IndexedItem> items = indexContainersInRange(task, level, blockCenter, range);
            return createIndexResultMessage(items, task.totalContainersSearched);
        } finally {
            activeTasks.remove(playerId);
        }
    }

    public Component cancelSearch(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return Component.translatable("info.cif.player_only").withStyle(ChatFormatting.RED);
        }

        Player player = source.getPlayer();
        assert player != null;

        SearchTask task = activeTasks.remove(player.getUUID());
        if (task == null) {
            return Component.translatable("info.cif.instant.no_active").withStyle(ChatFormatting.RED);
        }

        return task.cancel();
    }

    public static class SearchTask {
        private static final long HEARTBEAT_INTERVAL = 10_000; // 10 seconds in milliseconds
        private final ServerPlayer source;
        private final Level level;
        private final Vec3 center;
        private final int range;
        private final AtomicInteger blocksSearched = new AtomicInteger(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private long lastHeartbeatTime = 0;
        private int totalContainersSearched = 0;

        public SearchTask(ServerPlayer source, Level level, Vec3 center, int range) {
            this.source = source;
            this.level = level;
            this.center = center;
            this.range = range;
        }

        private Component createHeartbeatMessage(int blocksSearched, double currentDistance) {
            return Component.translatable("info.cif.instant.index.heartbeat",
                            blocksSearched, currentDistance)
                    .withStyle(ChatFormatting.GRAY);
        }

        private Component createIndexedContainerMessage(BlockPos pos) {
            return Component.translatable("info.cif.instant.index.found",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY);
        }

        private Component createCancelledMessage(int blocksSearched, double lastDistance) {
            return Component.translatable("info.cif.instant.index.cancel_info",
                            blocksSearched, lastDistance)
                    .withStyle(ChatFormatting.YELLOW);
        }

        private void sendHeartbeat(double currentDistance) {
            if (source != null && !cancelled.get()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
                    source.sendSystemMessage(createHeartbeatMessage(blocksSearched.get(), currentDistance));
                    lastHeartbeatTime = currentTime;
                }
            }
        }

        public Component cancel() {
            if (cancelled.compareAndSet(false, true) && source != null) {
                return createCancelledMessage(blocksSearched.get(),
                        Math.sqrt(
                                Math.pow(center.x, 2) +
                                        Math.pow(center.y, 2) +
                                        Math.pow(center.z, 2)
                        ));
            }
            return Component.translatable("info.cif.instant.index.cancel").withStyle(ChatFormatting.YELLOW);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }
} 