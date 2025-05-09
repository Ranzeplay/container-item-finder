package space.ranzeplay.containeritemfinder.service;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import java.util.ArrayList;
import java.util.List;

public class ChestSearchService {
    
    public Text searchChests(ServerWorld world, Vec3d center, int range, Item targetItem, int requiredCount) {
        List<BlockPos> foundContainers = new ArrayList<>();
        int totalFound = 0;

        BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);

        // First pass: collect all containers with the target item
        List<BlockPos> allContainersWithItem = new ArrayList<>();
        List<Integer> containerCounts = new ArrayList<>();

        // Iterate through all block positions in the search area
        for (int x = blockCenter.getX() - range; x <= blockCenter.getX() + range; x++) {
            for (int y = blockCenter.getY() - range; y <= blockCenter.getY() + range; y++) {
                for (int z = blockCenter.getZ() - range; z <= blockCenter.getZ() + range; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    
                    if (blockEntity instanceof ChestBlockEntity chest) {
                        int containerCount = 0;
                        // Check each slot in the chest
                        for (int i = 0; i < chest.size(); i++) {
                            ItemStack stack = chest.getStack(i);
                            if (stack.getItem() == targetItem) {
                                containerCount += stack.getCount();
                            }
                        }
                        
                        if (containerCount > 0) {
                            allContainersWithItem.add(pos);
                            containerCounts.add(containerCount);
                        }
                    } else if (blockEntity instanceof ShulkerBoxBlockEntity shulker) {
                        int containerCount = 0;
                        // Check each slot in the shulker box
                        for (int i = 0; i < shulker.size(); i++) {
                            ItemStack stack = shulker.getStack(i);
                            if (stack.getItem() == targetItem) {
                                containerCount += stack.getCount();
                            }
                        }
                        
                        if (containerCount > 0) {
                            allContainersWithItem.add(pos);
                            containerCounts.add(containerCount);
                        }
                    }
                }
            }
        }

        // Second pass: accumulate containers until we reach the required count
        if (requiredCount > 0) {
            for (int i = 0; i < allContainersWithItem.size(); i++) {
                foundContainers.add(allContainersWithItem.get(i));
                totalFound += containerCounts.get(i);
                if (totalFound >= requiredCount) {
                    break;
                }
            }
        } else {
            // If no required count, add all containers
            foundContainers.addAll(allContainersWithItem);
            totalFound = containerCounts.stream().mapToInt(Integer::intValue).sum();
        }

        // Generate result message
        if (foundContainers.isEmpty()) {
            return Text.literal("No containers found containing ")
                   .formatted(Formatting.RED)
                   .append(Text.literal((requiredCount > 0 ? requiredCount + "x " : "") + 
                          targetItem.getName().getString())
                          .formatted(Formatting.RED));
        } else {
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
    }
} 