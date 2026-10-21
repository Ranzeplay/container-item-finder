package space.ranzeplay.containeritemfinder.service;

import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.models.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;


public class TrackingService {
    private final Connection connection;
    private final DatabaseType databaseType;
    private final Logger logger;
    private final ThreadPoolExecutor scheduler;
    private final ThreadPoolExecutor instantScanScheduler;
    private final List<AABB> trackingAreas;

    private Date lastScan;
    private final long interval;
    @Getter
    private boolean scanning;

    private final ConcurrentLinkedQueue<Consumer<MinecraftServer>> instantScanQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DelayedLocationScan> delayedLocationScanQueue = new ConcurrentLinkedQueue<>();

    @Getter
    private TrackerScanStatistics latestStatistics;

    private static final int LOCATION_SCAN_DELAY_MS = 5000;

    public TrackingService(Config config) throws IllegalStateException {
        logger = Main.getLogger();
        databaseType = DatabaseType.fromConfig(config.getDatabaseType());

        trackingAreas = config.getTrackingAreas();
        interval = config.getRefreshIntervalMinutes();
        lastScan = Date.from(Instant.EPOCH);

        scheduler = new ThreadPoolExecutor(Math.min(2, config.getIndexThreads()), config.getIndexThreads(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        instantScanScheduler = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        try {
            connection = databaseType.connect(config.getDatabaseConnectionString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize tracking database", e);
        }
    }

    public void tryScan(MinecraftServer server) {
        if (connection == null || new Date().getTime() - lastScan.getTime() < interval * 60 * 1000 || scanning) {
            return;
        }

        scheduler.execute(() -> doScan(server));
    }

    public void manualScan(MinecraftServer server) {
        if (connection == null || scanning) {
            return;
        }

        scheduler.execute(() -> doScan(server));
    }

    private void doScan(MinecraftServer server) {
        try {
            logger.info("Beginning manual tracking area scan...");
            scanning = true;

            var beginTime = new Date();
            scan(server);
            var endTime = new Date();

            var stats = generateLatestStatistics(beginTime, endTime);
            lastScan = endTime;
            latestStatistics = stats;

            stats.log(logger);
            scanning = false;
        } catch (SQLException e) {
            logger.error("Failed to scan tracking areas: ", e);
        }
    }

    private void scan(MinecraftServer server) throws SQLException {
        var tasks = new ArrayList<Callable<Void>>();

        for (AABB area : trackingAreas) {
            tasks.add(() -> {
                scanAABB(server, area);
                return null;
            });
        }

        try {
            scheduler.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public void searchTrackingItem(CommandSourceStack commandSource, Level level, Vec3 center, Integer range, Item targetItem, Integer requiredCount) {
        try (PreparedStatement statement = connection.prepareStatement(
                DatabaseType.searchSql(range != null, requiredCount != null))) {
            var itemId = targetItem.getDescriptionId();

            statement.setDouble(1, (int) center.x());
            statement.setDouble(2, (int) center.y());
            statement.setDouble(3, (int) center.z());
            statement.setString(4, itemId);
            statement.setString(5, level.dimension().identifier().toString());
            if (range != null) {
                statement.setDouble(6, (double) range * range);
            }

            try (var rs = statement.executeQuery()) {
                var totalFound = 0;
                while ((requiredCount == null || totalFound < requiredCount) && rs.next()) {
                    var result = new TrackingSearchResult(rs);
                    commandSource.sendSystemMessage(result.toText());
                    totalFound += result.getCount();
                }

                if (requiredCount != null && totalFound < requiredCount) {
                    commandSource.sendSystemMessage(
                            Component.translatable("info.cif.db.scan.not_enough", totalFound, requiredCount).withStyle(ChatFormatting.RED));
                } else {
                    commandSource.sendSystemMessage(Component.translatable("info.cif.db.scan.complete", totalFound).withStyle(ChatFormatting.GREEN));
                }
            }

            if (scanning) {
                commandSource.sendSystemMessage(Component.translatable("info.cif.db.still_scanning").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private TrackerScanStatistics generateLatestStatistics(Date begin, Date end) throws SQLException {
        var duration = Duration.between(begin.toInstant(), end.toInstant());

        int containerCount = -1;
        var containerCountStmt = connection.prepareStatement("SELECT COUNT(*) FROM containers");
        var containerCountRs = containerCountStmt.executeQuery();
        if (containerCountRs.next()) {
            containerCount = containerCountRs.getInt(1);
        }
        containerCountStmt.close();

        int itemCount = -1;
        var itemCountStmt = connection.prepareStatement("SELECT SUM(count) FROM items");
        var itemCountRs = itemCountStmt.executeQuery();
        if (itemCountRs.next()) {
            itemCount = itemCountRs.getInt(1);
        }
        itemCountStmt.close();

        return new TrackerScanStatistics(
                trackingAreas.size(),
                containerCount,
                itemCount,
                duration
        );
    }

    private void scanAABB(MinecraftServer server, AABB area) throws SQLException {
        var levels = server.getAllLevels();
        Level level = null;
        for (var w : levels) {
            if (w.dimension().identifier().equals(Identifier.tryParse(area.getWorld()))) {
                level = w;
                break;
            }
        }

        if (level == null) {
            logger.warn("Level {} not found, skipping tracking area", area.getWorld());
            return;
        }

        var fromX = Math.min(area.getP1().getX(), area.getP2().getX());
        var toX = Math.max(area.getP1().getX(), area.getP2().getX());

        var fromY = Math.min(area.getP1().getY(), area.getP2().getY());
        var toY = Math.max(area.getP1().getY(), area.getP2().getY());

        var fromZ = Math.min(area.getP1().getZ(), area.getP2().getZ());
        var toZ = Math.max(area.getP1().getZ(), area.getP2().getZ());

        // Remove all existing entries in the area

        var dbClearStmt = connection.prepareStatement(
                "DELETE FROM containers WHERE world = ? AND x >= ? AND x <= ? AND y >= ? AND y <= ? AND z >= ? AND z <= ?"
        );

        dbClearStmt.setString(1, area.getWorld());
        dbClearStmt.setInt(2, fromX);
        dbClearStmt.setInt(3, toX);
        dbClearStmt.setInt(4, fromY);
        dbClearStmt.setInt(5, toY);
        dbClearStmt.setInt(6, fromZ);
        dbClearStmt.setInt(7, toZ);
        dbClearStmt.execute();
        dbClearStmt.close();

        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    scanOne(level, new BlockPos(x, y, z), false);
                }
            }
        }
    }

    public void scanOne(Level level, BlockPos pos, boolean removeExisting) throws SQLException {
        if(removeExisting) {
            removeBlockFromTracking(pos, level);
        }

        var blockState = level.getBlockState(pos);
        var blockEntity = level.getChunk(pos).getBlockEntity(pos);

        HashMap<String, Integer> items = tryGetContainerItems(blockEntity);
        if (items.isEmpty()) {
            return;
        }

        // Insert a new container or refresh the existing entry at these coordinates.
        try (var dbInsertStmt = connection.prepareStatement(databaseType.containerUpsertSql())) {
            databaseType.bindId(dbInsertStmt, 1, UUID.randomUUID());
            dbInsertStmt.setString(2, level.dimension().identifier().toString());
            dbInsertStmt.setInt(3, pos.getX());
            dbInsertStmt.setInt(4, pos.getY());
            dbInsertStmt.setInt(5, pos.getZ());
            dbInsertStmt.setString(6, blockState.getBlock().getDescriptionId());
            dbInsertStmt.executeUpdate();
        }

        UUID containerId;
        try (var statement = connection.prepareStatement("SELECT id FROM containers WHERE x = ? AND y = ? AND z = ? AND world = ?")) {
            statement.setInt(1, pos.getX());
            statement.setInt(2, pos.getY());
            statement.setInt(3, pos.getZ());
            statement.setString(4, level.dimension().identifier().toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return;
                }
                containerId = UUID.fromString(result.getString("id"));
            }
        }

        // Replace the item snapshot when the container already existed.
        try (var dbClearItemsStmt = connection.prepareStatement(
                "DELETE FROM items WHERE container = ?"
        )) {
            databaseType.bindId(dbClearItemsStmt, 1, containerId);
            dbClearItemsStmt.executeUpdate();
        }

        try (var dbItemStmt = connection.prepareStatement(
                "INSERT INTO items (id, item, count, container) VALUES (?, ?, ?, ?)"
        )) {
            for (var itemId : items.keySet()) {
                dbItemStmt.clearParameters();
                databaseType.bindId(dbItemStmt, 1, UUID.randomUUID());
                dbItemStmt.setString(2, itemId);
                dbItemStmt.setInt(3, items.get(itemId));
                databaseType.bindId(dbItemStmt, 4, containerId);
                dbItemStmt.executeUpdate();
            }
        }
    }

    private static @NotNull HashMap<String, Integer> tryGetContainerItems(BlockEntity blockEntity) {
        HashMap<String, Integer> items = new HashMap<>();

        RandomizableContainerBlockEntity container;
        if (blockEntity instanceof ChestBlockEntity chest) {
            container = chest;
        } else if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox) {
            container = shulkerBox;
        } else {
            return items;
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                var itemId = stack.getItem().getDescriptionId();
                items.merge(itemId, stack.getCount(), Integer::sum);

                tryGetAsShulkerBoxItems(stack).forEach((k, v) -> items.merge(k, v, Integer::sum));
                tryGetAsBundleItems(stack).forEach((k, v) -> items.merge(k, v, Integer::sum));
            }
        }
        return items;
    }

    private static @NotNull HashMap<String, Integer> tryGetAsShulkerBoxItems(ItemStack stack) {
        HashMap<String, Integer> items = new HashMap<>();

        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                var containerComponent = stack.get(DataComponents.CONTAINER);
                if (containerComponent != null) {
                    for(var innerStack : containerComponent.itemCopies().toList()) {
                        if (!innerStack.isEmpty()) {
                            var innerItemId = innerStack.getItem().getDescriptionId();
                            items.merge(innerItemId, innerStack.getCount(), Integer::sum);
                            tryGetAsBundleItems(innerStack).forEach((k, v) -> items.merge(k, v, Integer::sum));
                        }
                    }
                }
            }
        }

        return items;
    }

    private static HashMap<String, Integer> tryGetAsBundleItems(ItemStack stack) {
        HashMap<String, Integer> items = new HashMap<>();

        if (stack.getItem().getDescriptionId().equals("item.minecraft.bundle")) {
            var containerComponent = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (containerComponent != null) {
                for(var innerStack : containerComponent.itemCopies().toList()) {
                    if (!innerStack.isEmpty()) {
                        var innerItemId = innerStack.getItem().getDescriptionId();
                        items.merge(innerItemId, innerStack.getCount(), Integer::sum);
                    }
                }
            }
        }

        return items;
    }

    public void queueScan(Vec3 location, Level level, int radius) {
        instantScanQueue.add((server) -> {
            try {
                Point p1 = new Point((int) (location.x() - radius), (int) (location.y() - radius), (int) (location.z() - radius));
                Point p2 = new Point((int) (location.x() + radius), (int) (location.y() + radius), (int) (location.z() + radius));
                AABB aabb = new AABB(p1, p2, level.dimension().identifier().toString());

                logger.debug("Performing instant scan at {} @ {}", String.format("(%.1f, %.1f, %.1f)", location.x(), location.y(), location.z()), level.dimension().identifier());
                scanAABB(server, aabb);

            } catch (SQLException e) {
                logger.error("Failed to perform instant scan: ", e);
            }
        });
    }

    public void queueScan(Location location) {
        if(delayedLocationScanQueue.stream().anyMatch(p -> p.getLocation().equals(location))) {
            return;
        }

        delayedLocationScanQueue.add(new DelayedLocationScan(location, System.currentTimeMillis()));
    }

    public void applyScanQueue(MinecraftServer server) {
        // Apply delayed scans
        var now = System.currentTimeMillis();
        while(delayedLocationScanQueue.stream().anyMatch(p -> now - p.getSubmitTimeMillis() > LOCATION_SCAN_DELAY_MS)) {
            var task = delayedLocationScanQueue.poll();
            if (task != null) {
                instantScanQueue.add((s) -> {
                    try {
                        Level level = null;
                        for (var w : s.getAllLevels()) {
                            if (w.dimension().identifier().equals(Identifier.tryParse(task.getLocation().getLevel()))) {
                                level = w;
                                break;
                            }
                        }

                        scanOne(level, task.getLocation().toBlockPos(), true);
                        logger.debug("Performed delayed scan at {} @ {}", task.getLocation().toString(), task.getLocation().getLevel());
                    } catch (SQLException e) {
                        logger.error("Failed to perform delayed scan: ", e);
                    }
                });
            }
        }

        // Apply scans
        while (!instantScanQueue.isEmpty()) {
            var task = instantScanQueue.poll();
            if (task != null) {
                instantScanScheduler.execute(() -> task.accept(server));
            }
        }
    }

    public void removeBlockFromTracking(BlockPos pos, Level level) {
        if (connection == null) {
            return;
        }

        try {
            var stmt = connection.prepareStatement(
                    "DELETE FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?"
            );
            stmt.setString(1, level.dimension().identifier().toString());
            stmt.setInt(2, pos.getX());
            stmt.setInt(3, pos.getY());
            stmt.setInt(4, pos.getZ());
            stmt.execute();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed to remove block from tracking: ", e);
        }
    }
}
