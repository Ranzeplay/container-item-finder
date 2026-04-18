package space.ranzeplay.containeritemfinder.service;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Shared BFS traversal over blocks in a cubic range, visiting each chest or shulker box found.
 */
class ContainerBFSUtil {
    private static final int[][] DIRECTIONS = {
            {0, 1, 0},  // up
            {0, -1, 0}, // down
            {0, 0, -1}, // north
            {0, 0, 1},  // south
            {1, 0, 0},  // east
            {-1, 0, 0}  // west
    };

    /**
     * BFS over all blocks within {@code range} of {@code center}, calling {@code containerVisitor}
     * for each chest or shulker box encountered.
     *
     * @param containerVisitor receives (blockEntity, pos); return {@code true} to stop early.
     * @return total number of containers visited.
     */
    static int scan(Level level, BlockPos center, int range, BaseSearchTask task,
                    BiFunction<BlockEntity, BlockPos, Boolean> containerVisitor) {
        int totalContainersSearched = 0;
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.offer(center);
        visited.add(center);

        int currentDistance = 0;
        int nodesAtCurrentDistance = 1;
        int nodesAtNextDistance = 0;

        while (!queue.isEmpty() && !task.isCancelled()) {
            BlockPos current = queue.poll();
            assert current != null;
            nodesAtCurrentDistance--;

            BlockEntity blockEntity = level.getChunk(current).getBlockEntity(current);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                totalContainersSearched++;
                if (containerVisitor.apply(blockEntity, current)) {
                    break;
                }
            }

            if (currentDistance < range) {
                for (int[] dir : DIRECTIONS) {
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

            task.blocksSearched.incrementAndGet();
            double distance = Math.sqrt(
                    Math.pow(current.getX() - center.getX(), 2) +
                    Math.pow(current.getY() - center.getY(), 2) +
                    Math.pow(current.getZ() - center.getZ(), 2)
            );
            task.sendHeartbeat(distance);
        }

        return totalContainersSearched;
    }
}
