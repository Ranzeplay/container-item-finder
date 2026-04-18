package space.ranzeplay.containeritemfinder.service;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
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

public class ContainerSearchService {
    private static final Map<UUID, SearchTask> activeTasks = new ConcurrentHashMap<>();

    private static int countItemsInStack(ItemStack stack, Item targetItem) {
        if (stack.getItem().getDescriptionId().equals(targetItem.getDescriptionId())) {
            return stack.getCount();
        }
        return 0;
    }

    private static int countItemsInContainer(BlockEntity container, Item targetItem) {
        int totalCount = 0;

        if (container instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                totalCount += countItemsInStack(stack, targetItem);
            }
        } else if (container instanceof ShulkerBoxBlockEntity shulker) {
            for (int i = 0; i < shulker.getContainerSize(); i++) {
                ItemStack stack = shulker.getItem(i);
                totalCount += countItemsInStack(stack, targetItem);
            }
        }

        return totalCount;
    }

    private static List<ContainerInfo> findContainersInRange(SearchTask task, Level level, BlockPos center, int range, Item targetItem, int requiredCount) {
        List<ContainerInfo> containers = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        int totalFound = 0;
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

        while (!queue.isEmpty() && (requiredCount <= 0 || totalFound < requiredCount) && !task.isCancelled()) {
            BlockPos current = queue.poll();
            assert current != null;
            nodesAtCurrentDistance--;

            // Check if current position has a container with target item
            BlockEntity blockEntity = level.getChunk(current).getBlockEntity(current);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                totalContainersSearched++;
                int itemCount = countItemsInContainer(blockEntity, targetItem);
                if (itemCount > 0) {
                    containers.add(new ContainerInfo(current, itemCount));
                    totalFound += itemCount;

                    // Send message when a container with target items is found
                    if (task.source != null) {
                        task.source.sendSystemMessage(task.createFoundItemMessage(itemCount, current));
                    }

                    if (requiredCount > 0 && totalFound >= requiredCount) {
                        break;
                    }
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
        return containers;
    }

    private static Component createResultMessage(List<ContainerInfo> foundContainers, Item targetItem, int requiredCount, int totalFound, Vec3 center, int totalContainersSearched) {
        if (foundContainers.isEmpty()) {
            return Component.literal(String.format("No containers found containing %s %s",
                            requiredCount > 0 ? requiredCount + "x " : "",
                            Component.translatable(targetItem.getDescriptionId()).getString()))
                        .withStyle(ChatFormatting.RED);
        }

        // Calculate distances
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0;
        for (ContainerInfo container : foundContainers) {
            double distance = Math.sqrt(
                    Math.pow(container.pos.getX() - center.x(), 2) + Math.pow(container.pos.getY() - center.y(), 2) + Math.pow(container.pos.getZ() - center.z(), 2)
            );
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }

        MutableComponent message = Component.empty();
        
        // First line: Item count and containers found
        if (requiredCount > 0 && totalFound < requiredCount) {
            message.append(Component.translatable(
                                    "info.cif.instant.search.finish_1_1",
                                    totalFound,
                                    Component.translatable(targetItem.getDescriptionId()).getString(),
                                    requiredCount - totalFound,
                                    foundContainers.size()
                            )).withStyle(ChatFormatting.YELLOW);
        } else {
            message.append(Component.translatable(
                                    "info.cif.instant.search.finish_1_2",
                                    totalFound,
                                    Component.translatable(targetItem.getDescriptionId()).getString(),
                                    foundContainers.size()
                            )).withStyle(ChatFormatting.GREEN);
        }
        message.append(Component.literal("\n"));

        // Second line: Search statistics
        message.append(Component.translatable(
                        "info.cif.instant.search.finish_2",
                        totalContainersSearched, minDistance, maxDistance
                )).withStyle(ChatFormatting.GRAY);
        message.append(Component.literal("\n"));

        // Third line: Container positions
        message.append(Component.translatable("info.cif.instant.search.finish_3")
                .withStyle(ChatFormatting.GRAY));
        for (ContainerInfo container : foundContainers) {
            message.append(Component.literal(String.format(" [%d, %d, %d]",
                            container.pos.getX(), container.pos.getY(), container.pos.getZ()))
                    .withStyle(ChatFormatting.AQUA));
        }

        return message;
    }

    public Component searchChests(CommandSourceStack source, Level level, Vec3 center, int range, Item targetItem, int requiredCount) {
        if (!source.isPlayer()) {
            return Component.translatable("info.cif.player_only").withStyle(ChatFormatting.RED);
        }

        var player = source.getPlayer();
        assert player != null;

        UUID playerId = player.getUUID();
        if (activeTasks.containsKey(playerId)) {
            return Component.translatable("info.cif.instant.task_wip").withStyle(ChatFormatting.RED);
        }

        SearchTask task = new SearchTask(player, level, center, range, targetItem, requiredCount);
        activeTasks.put(playerId, task);
        return task.execute();
    }

    public Component cancelSearch(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return Component.translatable("info.cif.player_only").withStyle(ChatFormatting.RED);
        }

        ServerPlayer player = source.getPlayer();
        assert player != null;

        SearchTask task = activeTasks.remove(player.getUUID());
        if (task == null) {
            return Component.translatable("info.cif.instant.no_active").withStyle(ChatFormatting.RED);
        }

        return task.cancel();
    }

    private record ContainerInfo(BlockPos pos, int itemCount) {
    }

    public static class SearchTask {
        private static final long HEARTBEAT_INTERVAL = 10_000; // 10 seconds in milliseconds
        private final ServerPlayer source;
        private final Level level;
        private final Vec3 center;
        private final int range;
        private final Item targetItem;
        private final int requiredCount;
        private final AtomicInteger blocksSearched = new AtomicInteger(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private long lastHeartbeatTime = 0;
        private int totalContainersSearched = 0;

        public SearchTask(ServerPlayer source, Level level, Vec3 center, int range, Item targetItem, int requiredCount) {
            this.source = source;
            this.level = level;
            this.center = center;
            this.range = range;
            this.targetItem = targetItem;
            this.requiredCount = requiredCount;
        }

        private Component createHeartbeatMessage(int blocksSearched, double currentDistance) {
            return Component.translatable("info.cif.instant.search.heartbeat", blocksSearched, currentDistance)
                    .withStyle(ChatFormatting.GRAY);
        }

        private Component createFoundItemMessage(int itemCount, BlockPos pos) {
            return Component.translatable("info.cif.instant.search.found",
                            itemCount, Component.translatable(targetItem.getDescriptionId()).getString(),
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY);
        }

        private Component createCancelledMessage(int blocksSearched, double lastDistance) {
            return Component.translatable("info.cif.instant.search.cancel_info", blocksSearched, lastDistance)
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
            return Component.translatable("info.cif.instant.search.cancel").withStyle(ChatFormatting.YELLOW);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public Component execute() {
            try {
                BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);
                List<ContainerInfo> containers = findContainersInRange(this, level, blockCenter, range, targetItem, requiredCount);
                int totalFound = containers.stream().mapToInt(ContainerInfo::itemCount).sum();
                return createResultMessage(containers, targetItem, requiredCount, totalFound, center, totalContainersSearched);
            } finally {
                if (source != null) {
                    activeTasks.remove(source.getUUID());
                }
            }
        }
    }
}
