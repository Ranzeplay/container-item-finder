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

import java.util.ArrayList;
import java.util.List;

public class ChestSearchService {

    private int countItemsInStack(ItemStack stack, Item targetItem) {
        if (stack.getItem().getTranslationKey().equals(targetItem.getTranslationKey())) {
            return stack.getCount();
        } else {
            return 0;
        }
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

    private List<BlockPos> findContainersInRange(ServerWorld world, BlockPos center, int range, Item targetItem) {
        List<BlockPos> containers = new ArrayList<>();

        // Start from center
        int x = center.getX();
        int y = center.getY();
        int z = center.getZ();

        // Spiral search pattern
        for (int layer = 0; layer <= range; layer++) {
            // Search each layer in a clockwise spiral
            for (int dx = -layer; dx <= layer; dx++) {
                // Top edge
                BlockPos pos = new BlockPos(x + dx, y + layer, z - layer);
                checkAndAddContainer(world, pos, containers, targetItem);
            }

            for (int dz = -layer + 1; dz <= layer; dz++) {
                // Right edge
                BlockPos pos = new BlockPos(x + layer, y + layer, z + dz);
                checkAndAddContainer(world, pos, containers, targetItem);
            }

            for (int dx = layer - 1; dx >= -layer; dx--) {
                // Bottom edge
                BlockPos pos = new BlockPos(x + dx, y + layer, z + layer);
                checkAndAddContainer(world, pos, containers, targetItem);
            }

            for (int dz = layer - 1; dz > -layer; dz--) {
                // Left edge
                BlockPos pos = new BlockPos(x - layer, y + layer, z + dz);
                checkAndAddContainer(world, pos, containers, targetItem);
            }

            // Search lower layers
            for (int dy = layer - 1; dy >= -layer; dy--) {
                // Top edge
                for (int dx = -layer; dx <= layer; dx++) {
                    BlockPos pos = new BlockPos(x + dx, y + dy, z - layer);
                    checkAndAddContainer(world, pos, containers, targetItem);
                }

                // Right edge
                for (int dz = -layer + 1; dz <= layer; dz++) {
                    BlockPos pos = new BlockPos(x + layer, y + dy, z + dz);
                    checkAndAddContainer(world, pos, containers, targetItem);
                }

                // Bottom edge
                for (int dx = layer - 1; dx >= -layer; dx--) {
                    BlockPos pos = new BlockPos(x + dx, y + dy, z + layer);
                    checkAndAddContainer(world, pos, containers, targetItem);
                }

                // Left edge
                for (int dz = layer - 1; dz > -layer; dz--) {
                    BlockPos pos = new BlockPos(x - layer, y + dy, z + dz);
                    checkAndAddContainer(world, pos, containers, targetItem);
                }
            }
        }

        return containers;
    }

    private void checkAndAddContainer(ServerWorld world, BlockPos pos, List<BlockPos> containers, Item targetItem) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
            if (countItemsInContainer(blockEntity, targetItem) > 0) {
                containers.add(pos);
            }
        }
    }

    private List<BlockPos> filterContainersByRequiredCount(ServerWorld world, List<BlockPos> containers, Item targetItem, int requiredCount) {
        if (requiredCount <= 0) {
            return containers;
        }

        List<BlockPos> filteredContainers = new ArrayList<>();
        int totalFound = 0;

        for (BlockPos pos : containers) {
            filteredContainers.add(pos);
            totalFound += countItemsInContainer(world.getBlockEntity(pos), targetItem);
            if (totalFound >= requiredCount) {
                break;
            }
        }

        return filteredContainers;
    }

    private Text createResultMessage(List<BlockPos> foundContainers, Item targetItem, int requiredCount, int totalFound) {
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

        for (BlockPos pos : foundContainers) {
            message.append(Text.literal(String.format("[%d, %d, %d] ", pos.getX(), pos.getY(), pos.getZ()))
                    .formatted(Formatting.AQUA));
        }

        return message;
    }

    public Text searchChests(ServerWorld world, Vec3d center, int range, Item targetItem, int requiredCount) {
        BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);

        // Find all containers with the target item
        List<BlockPos> containers = findContainersInRange(world, blockCenter, range, targetItem);

        // Filter containers based on required count
        List<BlockPos> filteredContainers = filterContainersByRequiredCount(world, containers, targetItem, requiredCount);

        // Calculate total items found
        int totalFound = 0;
        for (BlockPos pos : filteredContainers) {
            totalFound += countItemsInContainer(world.getBlockEntity(pos), targetItem);
        }

        // Generate and return result message
        return createResultMessage(filteredContainers, targetItem, requiredCount, totalFound);
    }
} 