package space.ranzeplay.chestitemfinder.service;

import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
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
        List<BlockPos> foundChests = new ArrayList<>();
        int totalFound = 0;

        BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);

        // First pass: collect all chests with the target item
        List<BlockPos> allChestsWithItem = new ArrayList<>();
        List<Integer> chestCounts = new ArrayList<>();

        // Iterate through all block positions in the search area
        for (int x = blockCenter.getX() - range; x <= blockCenter.getX() + range; x++) {
            for (int y = blockCenter.getY() - range; y <= blockCenter.getY() + range; y++) {
                for (int z = blockCenter.getZ() - range; z <= blockCenter.getZ() + range; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                        BlockEntity blockEntity = world.getBlockEntity(pos);
                        if (blockEntity instanceof ChestBlockEntity chest) {
                            int chestCount = 0;
                            // Check each slot in the chest
                            for (int i = 0; i < chest.size(); i++) {
                                ItemStack stack = chest.getStack(i);
                                if (stack.getItem() == targetItem) {
                                    chestCount += stack.getCount();
                                }
                            }
                            
                            if (chestCount > 0) {
                                allChestsWithItem.add(pos);
                                chestCounts.add(chestCount);
                            }
                        }
                    }
                }
            }
        }

        // Second pass: accumulate chests until we reach the required count
        if (requiredCount > 0) {
            for (int i = 0; i < allChestsWithItem.size(); i++) {
                foundChests.add(allChestsWithItem.get(i));
                totalFound += chestCounts.get(i);
                if (totalFound >= requiredCount) {
                    break;
                }
            }
        } else {
            // If no required count, add all chests
            foundChests.addAll(allChestsWithItem);
            totalFound = chestCounts.stream().mapToInt(Integer::intValue).sum();
        }

        // Generate result message
        if (foundChests.isEmpty()) {
            return Text.literal("No chests found containing ")
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
                    .append(Text.literal(foundChests.size() + " chests at positions: ")
                        .formatted(Formatting.YELLOW));
            } else {
                message = Text.literal("Found ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(totalFound + "x " + targetItem.getName().getString())
                        .formatted(Formatting.GREEN))
                    .append(Text.literal(" in " + foundChests.size() + " chests at positions: ")
                        .formatted(Formatting.GREEN));
            }
            
            for (BlockPos pos : foundChests) {
                message.append(Text.literal(String.format("[%d, %d, %d] ", pos.getX(), pos.getY(), pos.getZ()))
                    .formatted(Formatting.AQUA));
            }
            
            return message;
        }
    }
} 