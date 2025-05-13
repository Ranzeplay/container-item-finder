package space.ranzeplay.containeritemfinder.service;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
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

public class ContainerIndexService {
    private static final Map<UUID, SearchTask> activeTasks = new ConcurrentHashMap<>();

    private record IndexedItem(String itemName, int count, BlockPos containerPos) {
    }

    private static List<IndexedItem> indexItemsInContainer(BlockEntity container, BlockPos pos) {
        List<IndexedItem> items = new ArrayList<>();
        
        if (container instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (!stack.isEmpty()) {
                    items.add(new IndexedItem(
                        stack.getItem().getName().getString(),
                        stack.getCount(),
                        pos
                    ));
                }
            }
        } else if (container instanceof ShulkerBoxBlockEntity shulker) {
            for (int i = 0; i < shulker.size(); i++) {
                ItemStack stack = shulker.getStack(i);
                if (!stack.isEmpty()) {
                    items.add(new IndexedItem(
                        stack.getItem().getName().getString(),
                        stack.getCount(),
                        pos
                    ));
                }
            }
        }
        
        return items;
    }

    private static List<IndexedItem> indexContainersInRange(SearchTask task, ServerWorld world, BlockPos center, int range) {
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
            BlockEntity blockEntity = world.getChunk(current).getBlockEntity(current);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                totalContainersSearched++;
                allItems.addAll(indexItemsInContainer(blockEntity, current));

                // Send message when a container is indexed
                if (task.source != null) {
                    task.source.sendMessage(task.createIndexedContainerMessage(current));
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

    private static Text createIndexResultMessage(List<IndexedItem> items, int totalContainersSearched) {
        if (items.isEmpty()) {
            return Text.literal("No items found in containers.")
                    .formatted(Formatting.RED);
        }

        MutableText message = Text.empty();
        
        // First line: Summary
        message.append(Text.literal("Indexed " + items.size() + " items in " + totalContainersSearched + " containers")
                .formatted(Formatting.GREEN))
                .append(Text.literal("\n"));

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
            
            message.append(Text.literal(String.format("%dx %s in %d containers\n",
                    totalCount, itemName, locations.size()))
                    .formatted(Formatting.AQUA));
        }

        return message;
    }

    public Text indexContainers(ServerCommandSource source, ServerWorld world, Vec3d center, int range) {
        if (!source.isExecutedByPlayer()) {
            return Text.literal("This command can only be used by players.").formatted(Formatting.RED);
        }

        ServerPlayerEntity player = source.getPlayer();
        assert player != null;

        UUID playerId = player.getUuid();
        if (activeTasks.containsKey(playerId)) {
            return Text.literal("You already have an active search task. Use '/cif cancel' to cancel it first.").formatted(Formatting.RED);
        }

        SearchTask task = new SearchTask(player, world, center, range);
        activeTasks.put(playerId, task);
        
        try {
            BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);
            List<IndexedItem> items = indexContainersInRange(task, world, blockCenter, range);
            return createIndexResultMessage(items, task.totalContainersSearched);
        } finally {
            if (source != null) {
                activeTasks.remove(playerId);
            }
        }
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

    public static class SearchTask {
        private static final long HEARTBEAT_INTERVAL = 10_000; // 10 seconds in milliseconds
        private final ServerPlayerEntity source;
        private final ServerWorld world;
        private final Vec3d center;
        private final int range;
        private final AtomicInteger blocksSearched = new AtomicInteger(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private long lastHeartbeatTime = 0;
        private int totalContainersSearched = 0;

        public SearchTask(ServerPlayerEntity source, ServerWorld world, Vec3d center, int range) {
            this.source = source;
            this.world = world;
            this.center = center;
            this.range = range;
        }

        private Text createHeartbeatMessage(int blocksSearched, double currentDistance) {
            return Text.literal(String.format("Searching... (%d blocks searched, %.1fm from center)",
                            blocksSearched, currentDistance))
                    .formatted(Formatting.GRAY);
        }

        private Text createIndexedContainerMessage(BlockPos pos) {
            return Text.literal(String.format("Indexed container at [%d, %d, %d]",
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
    }
} 