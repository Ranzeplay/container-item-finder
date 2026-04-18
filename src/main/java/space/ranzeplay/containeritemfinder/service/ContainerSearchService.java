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
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        RandomizableContainerBlockEntity c;
        if (container instanceof ChestBlockEntity chest) {
            c = chest;
        } else if (container instanceof ShulkerBoxBlockEntity shulker) {
            c = shulker;
        } else {
            return 0;
        }
        int totalCount = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            totalCount += countItemsInStack(c.getItem(i), targetItem);
        }
        return totalCount;
    }

    private static List<ContainerInfo> findContainersInRange(SearchTask task, Level level, BlockPos center, int range, Item targetItem, int requiredCount) {
        List<ContainerInfo> containers = new ArrayList<>();
        AtomicInteger totalFound = new AtomicInteger(0);
        task.totalContainersSearched = ContainerBFSUtil.scan(level, center, range, task, (blockEntity, pos) -> {
            int itemCount = countItemsInContainer(blockEntity, targetItem);
            if (itemCount > 0) {
                containers.add(new ContainerInfo(pos, itemCount));
                totalFound.addAndGet(itemCount);
                if (task.source != null) {
                    task.source.sendSystemMessage(task.createFoundItemMessage(itemCount, pos));
                }
                return requiredCount > 0 && totalFound.get() >= requiredCount;
            }
            return false;
        });
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

    public static class SearchTask extends BaseSearchTask {
        private final Level level;
        private final int range;
        private final Item targetItem;
        private final int requiredCount;

        public SearchTask(ServerPlayer source, Level level, Vec3 center, int range, Item targetItem, int requiredCount) {
            super(source, center,
                    "info.cif.instant.search.heartbeat",
                    "info.cif.instant.search.cancel_info",
                    "info.cif.instant.search.cancel");
            this.level = level;
            this.range = range;
            this.targetItem = targetItem;
            this.requiredCount = requiredCount;
        }

        Component createFoundItemMessage(int itemCount, BlockPos pos) {
            return Component.translatable("info.cif.instant.search.found",
                            itemCount, Component.translatable(targetItem.getDescriptionId()).getString(),
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY);
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

