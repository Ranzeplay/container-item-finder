package space.ranzeplay.containeritemfinder.service;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerIndexService {
    private static final Map<UUID, SearchTask> activeTasks = new ConcurrentHashMap<>();

    public record IndexedItem(String itemName, String id, int count, BlockPos containerPos) {
    }

    public static List<IndexedItem> indexItemsInContainer(BlockEntity container, BlockPos pos) {
        List<IndexedItem> items = new ArrayList<>();
        RandomizableContainerBlockEntity c;
        if (container instanceof ChestBlockEntity chest) {
            c = chest;
        } else if (container instanceof ShulkerBoxBlockEntity shulker) {
            c = shulker;
        } else {
            return items;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack stack = c.getItem(i);
            if (!stack.isEmpty()) {
                items.add(new IndexedItem(
                        stack.getItem().getName(stack).getString(),
                        stack.getItem().getDescriptionId(),
                        stack.getCount(),
                        pos
                ));
            }
        }
        return items;
    }

    private static List<IndexedItem> indexContainersInRange(SearchTask task, Level level, BlockPos center, int range) {
        List<IndexedItem> allItems = new ArrayList<>();
        task.totalContainersSearched = ContainerBFSUtil.scan(level, center, range, task, (blockEntity, pos) -> {
            allItems.addAll(indexItemsInContainer(blockEntity, pos));
            if (task.source != null) {
                task.source.sendSystemMessage(task.createIndexedContainerMessage(pos));
            }
            return false;
        });
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

        SearchTask task = new SearchTask(player, center);
        activeTasks.put(playerId, task);

        try {
            BlockPos blockCenter = new BlockPos((int) center.x, (int) center.y, (int) center.z);
            List<IndexedItem> items = indexContainersInRange(task, level, blockCenter, range);
            return createIndexResultMessage(items, task.totalContainersSearched);
        } finally {
            activeTasks.remove(playerId);
        }
    }

    public static class SearchTask extends BaseSearchTask {
        public SearchTask(ServerPlayer source, Vec3 center) {
            super(source, center,
                    "info.cif.instant.index.heartbeat",
                    "info.cif.instant.index.cancel_info",
                    "info.cif.instant.index.cancel");
        }

        Component createIndexedContainerMessage(BlockPos pos) {
            return Component.translatable("info.cif.instant.index.found",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY);
        }
    }
}