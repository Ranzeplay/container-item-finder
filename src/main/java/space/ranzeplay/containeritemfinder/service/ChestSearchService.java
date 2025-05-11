package space.ranzeplay.containeritemfinder.service;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChestSearchService {
    private static final Map<UUID, SearchTask> activeTasks = new ConcurrentHashMap<>();

    private record ContainerInfo(BlockPos pos, int itemCount) {}

    public static class SearchTask {
        private final ServerPlayerEntity source;
        private final ServerWorld world;
        private final Vec3d center;
        private final int range;
        private final Item targetItem;
        private final int requiredCount;
        private final AtomicInteger blocksSearched = new AtomicInteger(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private long lastHeartbeatTime = 0;
        private static final long HEARTBEAT_INTERVAL = 10_000; // 10 seconds in milliseconds

        public SearchTask(ServerPlayerEntity source, ServerWorld world, Vec3d center, int range, Item targetItem, int requiredCount) {
            this.source = source;
            this.world = world;
            this.center = center;
            this.range = range;
            this.targetItem = targetItem;
            this.requiredCount = requiredCount;
        }

        private Text createHeartbeatMessage(int blocksSearched, double currentDistance) {
            return Text.literal(String.format("Searching... (%d blocks searched, %.1fm from center)",
                    blocksSearched, currentDistance))
                    .formatted(Formatting.GRAY);
        }

        private Text createFoundItemMessage(int itemCount, BlockPos pos) {
            return Text.literal(String.format("Found %dx %s at [%d, %d, %d]",
                    itemCount, targetItem.getName().getString(),
                    pos.getX(), pos.getY(), pos.getZ()))
                    .formatted(Formatting.GRAY);
        }

        private Text createCancelledMessage(int blocksSearched, double lastDistance) {
            return Text.literal(String.format("Search cancelled. Searched %d blocks, last distance: %.1fm",
                    blocksSearched, lastDistance))
                    .formatted(Formatting.YELLOW);
        }

        private void sendHeartbeat(double currentDistance) {
            if (source != null && !cancelled.get()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
                    source.sendMessage(createHeartbeatMessage(blocksSearched.get(), currentDistance));
                    lastHeartbeatTime = currentTime;
                }
            }
        }

        public Text cancel() {
            if (cancelled.compareAndSet(false, true) && source != null) {
                return createCancelledMessage(blocksSearched.get(), 
                        Math.sqrt(
                            Math.pow(center.x, 2) +
                            Math.pow(center.y, 2) +
                            Math.pow(center.z, 2)
                        ));
            }
            return Text.literal("Search task cancelled.").formatted(Formatting.YELLOW);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public Text execute() {
            try {
                BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);
                List<ContainerInfo> containers = findContainersInRange(this, world, blockCenter, range, targetItem, requiredCount);
                int totalFound = containers.stream().mapToInt(ContainerInfo::itemCount).sum();
                return createResultMessage(containers, targetItem, requiredCount, totalFound);
            } finally {
                if (source != null) {
                    activeTasks.remove(source.getUuid());
                }
            }
        }
    }

    private static int countItemsInStack(ItemStack stack, Item targetItem) {
        if (stack.getItem().getTranslationKey().equals(targetItem.getTranslationKey())) {
            return stack.getCount();
        }
        return 0;
    }

    private static int countItemsInContainer(BlockEntity container, Item targetItem) {
        int totalCount = 0;

        if (container instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                totalCount += countItemsInStack(stack, targetItem);
            }
        } else if (container instanceof ShulkerBoxBlockEntity shulker) {
            for (int i = 0; i < shulker.size(); i++) {
                ItemStack stack = shulker.getStack(i);
                totalCount += countItemsInStack(stack, targetItem);
            }
        }

        return totalCount;
    }

    private static List<ContainerInfo> findContainersInRange(SearchTask task, ServerWorld world, BlockPos center, int range, Item targetItem, int requiredCount) {
        List<ContainerInfo> containers = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        int totalFound = 0;
        
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
            BlockEntity blockEntity = world.getChunk(current).getBlockEntity(current);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                int itemCount = countItemsInContainer(blockEntity, targetItem);
                if (itemCount > 0) {
                    containers.add(new ContainerInfo(current, itemCount));
                    totalFound += itemCount;
                    
                    // Send message when a container with target items is found
                    if (task.source != null) {
                        task.source.sendMessage(task.createFoundItemMessage(itemCount, current));
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
                Math.pow(current.getX() - center.getX(), 2) +
                Math.pow(current.getY() - center.getY(), 2) +
                Math.pow(current.getZ() - center.getZ(), 2)
            );
            task.sendHeartbeat(distance);
        }

        return containers;
    }

    private static Text createResultMessage(List<ContainerInfo> foundContainers, Item targetItem, int requiredCount, int totalFound) {
        if (foundContainers.isEmpty()) {
            return Text.literal("No containers found containing ")
                    .formatted(Formatting.RED)
                    .append(Text.literal((requiredCount > 0 ? requiredCount + "x " : "") +
                                    targetItem.getName().getString())
                            .formatted(Formatting.RED));
        }

        // Calculate distances
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0;
        for (ContainerInfo container : foundContainers) {
            double distance = Math.sqrt(
                container.pos.getX() * container.pos.getX() +
                container.pos.getY() * container.pos.getY() +
                container.pos.getZ() * container.pos.getZ()
            );
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }

        MutableText message;
        if (requiredCount > 0 && totalFound < requiredCount) {
            message = Text.literal("Found ")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal(totalFound + "x " + targetItem.getName().getString())
                            .formatted(Formatting.YELLOW))
                    .append(Text.literal(" (need " + (requiredCount - totalFound) + " more) in ")
                            .formatted(Formatting.YELLOW))
                    .append(Text.literal(foundContainers.size() + " containers at positions: ")
                            .formatted(Formatting.YELLOW));
        } else {
            message = Text.literal("Found ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(totalFound + "x " + targetItem.getName().getString())
                            .formatted(Formatting.GREEN))
                    .append(Text.literal(" in " + foundContainers.size() + " containers at positions: ")
                            .formatted(Formatting.GREEN));
        }

        // Add distance range information
        message.append(Text.literal(String.format(" (%.1f~%.1fm) ", minDistance, maxDistance))
                .formatted(Formatting.GRAY));

        for (ContainerInfo container : foundContainers) {
            message.append(Text.literal(String.format("[%d, %d, %d] ", 
                    container.pos.getX(), container.pos.getY(), container.pos.getZ()))
                    .formatted(Formatting.AQUA));
        }

        return message;
    }

    public Text searchChests(ServerCommandSource source, ServerWorld world, Vec3d center, int range, Item targetItem, int requiredCount) {
        if (!source.isExecutedByPlayer()) {
            return Text.literal("This command can only be used by players.").formatted(Formatting.RED);
        }

        ServerPlayerEntity player = source.getPlayer();
        assert player != null;

        UUID playerId = source.getPlayer().getUuid();
        if (activeTasks.containsKey(playerId)) {
            return Text.literal("You already have an active search task. Use '/cif cancel' to cancel it first.").formatted(Formatting.RED);
        }

        SearchTask task = new SearchTask(player, world, center, range, targetItem, requiredCount);
        activeTasks.put(playerId, task);
        return task.execute();
    }

    public Text cancelSearch(ServerCommandSource source) {
        if (!source.isExecutedByPlayer()) {
            return Text.literal("This command can only be used by players.").formatted(Formatting.RED);
        }

        ServerPlayerEntity player = source.getPlayer();
        assert player != null;

        SearchTask task = activeTasks.remove(player.getUuid());
        if (task == null) {
            return Text.literal("You don't have any active search tasks.").formatted(Formatting.RED);
        }


        return task.cancel();
    }
} 