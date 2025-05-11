package space.ranzeplay.containeritemfinder.service;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class ChestSearchService {

    private record ContainerInfo(BlockPos pos, int itemCount) {}

    private int countItemsInStack(ItemStack stack, Item targetItem) {
        if (stack.getItem().getTranslationKey().equals(targetItem.getTranslationKey())) {
            return stack.getCount();
        }
        return 0;
    }

    private int countItemsInContainer(BlockEntity container, Item targetItem) {
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

    private List<ContainerInfo> findContainersInRange(ServerWorld world, BlockPos center, int range, Item targetItem, int requiredCount) {
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

        while (!queue.isEmpty() && (requiredCount <= 0 || totalFound < requiredCount)) {
            BlockPos current = queue.poll();
            nodesAtCurrentDistance--;

            // Check if current position has a container with target item
            BlockEntity blockEntity = world.getChunk(current).getBlockEntity(current);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                int itemCount = countItemsInContainer(blockEntity, targetItem);
                if (itemCount > 0) {
                    containers.add(new ContainerInfo(current, itemCount));
                    totalFound += itemCount;
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
        }

        return containers;
    }

    private Text createResultMessage(List<ContainerInfo> foundContainers, Item targetItem, int requiredCount, int totalFound) {
        if (foundContainers.isEmpty()) {
            return Text.literal("No containers found containing ")
                    .formatted(Formatting.RED)
                    .append(Text.literal((requiredCount > 0 ? requiredCount + "x " : "") +
                                    targetItem.getName().getString())
                            .formatted(Formatting.RED));
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

        for (ContainerInfo container : foundContainers) {
            message.append(Text.literal(String.format("[%d, %d, %d] ", 
                    container.pos.getX(), container.pos.getY(), container.pos.getZ()))
                    .formatted(Formatting.AQUA));
        }

        return message;
    }

    public Text searchChests(ServerWorld world, Vec3d center, int range, Item targetItem, int requiredCount) {
        BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);

        // Find containers and count items in a single pass
        List<ContainerInfo> containers = findContainersInRange(world, blockCenter, range, targetItem, requiredCount);

        // Calculate total items found
        int totalFound = containers.stream().mapToInt(ContainerInfo::itemCount).sum();

        // Generate and return result message
        return createResultMessage(containers, targetItem, requiredCount, totalFound);
    }
} 