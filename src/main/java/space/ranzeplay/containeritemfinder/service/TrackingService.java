package space.ranzeplay.containeritemfinder.service;

import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.models.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;


public class TrackingService {
    private Connection connection;
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

    public TrackingService(Config config) throws IOException, IllegalStateException {
        logger = Main.getLogger();

        trackingAreas = config.getTrackingAreas();
        interval = config.getRefreshIntervalMinutes();
        lastScan = Date.from(Instant.EPOCH);

        scheduler = new ThreadPoolExecutor(Math.min(2, config.getIndexThreads()), config.getIndexThreads(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        instantScanScheduler = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        try {
            connection = DriverManager.getConnection(config.getDatabaseConnectionString());
        } catch (Exception e) {
            logger.error("Failed to connect to the database: ", e);
            connection = null;
            return;
        }

        // Migrate database schema
        final var path = getClass().getClassLoader().getResource("init.sql");
        if (path == null) {
            logger.error("Failed to find database migration script.");
            throw new IllegalStateException("Failed to find database migration script.");
        }

        final var stream = getClass().getClassLoader().getResourceAsStream("init.sql");
        if (stream == null) {
            logger.error("Failed to load database migration script.");
            throw new IllegalStateException("Failed to load database migration script.");
        }

        var reader = new BufferedReader(new InputStreamReader(stream));
        var sql = reader.lines().reduce("", (a, b) -> a + "\n" + b);
        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            logger.error("Failed to migrate database schema: ", e);
            connection = null;
            throw new IllegalStateException("Failed to migrate database schema: ", e);
        } finally {
            reader.close();
            stream.close();
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
    public void searchTrackingItem(ServerCommandSource commandSource, World world, Vec3d center, Integer range, Item targetItem, Integer requiredCount) {
        PreparedStatement statement;

        if (range == null) {
            statement = connection.prepareStatement(
                    """
                            SELECT *
                            FROM (
                                     SELECT items.count, containers.world, containers.x, containers.y, containers.z,
                                            distance(containers.x, containers.y, containers.z, ?, ?, ?) AS dist
                                     FROM items
                                              JOIN containers ON items.container = containers.id
                                     WHERE items.item = ?
                                       AND containers.world = ?
                                 ) sub
                            ORDER BY sub.dist DESC
                            """
            );
        } else {
            statement = connection.prepareStatement(
                    """
                            SELECT *
                            FROM (
                                     SELECT items.count, containers.world, containers.x, containers.y, containers.z,
                                            distance(containers.x, containers.y, containers.z, ?, ?, ?) AS dist
                                     FROM items
                                              JOIN containers ON items.container = containers.id
                                     WHERE items.item = ?
                                       AND containers.world = ?
                                 ) sub
                            WHERE sub.dist <= ?
                            ORDER BY sub.dist DESC
                            """
            );
        }


        var itemId = targetItem.getTranslationKey();

        statement.setInt(1, (int) center.getX());
        statement.setInt(2, (int) center.getY());
        statement.setInt(3, (int) center.getZ());
        statement.setString(4, itemId);
        statement.setString(5, world.getRegistryKey().getValue().toString());
        if (range != null) {
            statement.setInt(6, range);
        }

        var rs = statement.executeQuery();
        var totalFound = 0;
        if (requiredCount != null) {
            rs.afterLast();
            while (totalFound < requiredCount && rs.previous()) {
                var result = new TrackingSearchResult(rs);
                commandSource.sendMessage(result.toText());
                totalFound += result.getCount();
            }

            if (totalFound < requiredCount) {
                commandSource.sendMessage(
                        Text.translatable("info.cif.db.scan.not_enough", totalFound, requiredCount).formatted(Formatting.RED));
            } else {
                commandSource.sendMessage(Text.translatable("info.cif.db.scan.complete", totalFound).formatted(Formatting.GREEN));
            }
        } else {
            while (rs.next()) {
                var result = new TrackingSearchResult(rs);
                commandSource.sendMessage(result.toText());
                totalFound += result.getCount();
            }

            commandSource.sendMessage(Text.translatable("info.cif.db.scan.complete", totalFound).formatted(Formatting.GREEN));
        }

        if (scanning) {
            commandSource.sendMessage(Text.translatable("info.cif.db.still_scanning").formatted(Formatting.YELLOW));
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
        var worlds = server.getWorlds();
        World world = null;
        for (var w : worlds) {
            if (w.getRegistryKey().getValue().equals(Identifier.tryParse(area.getWorld()))) {
                world = w;
                break;
            }
        }

        if (world == null) {
            logger.warn("World {} not found, skipping tracking area", area.getWorld());
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
                    scanOne(world, new BlockPos(x, y, z), false);
                }
            }
        }
    }

    public void scanOne(World world, BlockPos pos, boolean removeExisting) throws SQLException {
        if(removeExisting) {
            removeBlockFromTracking(pos, world);
        }

        var blockState = world.getBlockState(pos);
        var blockEntity = world.getChunk(pos).getBlockEntity(pos);

        HashMap<String, Integer> items = tryGetContainerItems(blockEntity);
        if (items.isEmpty()) {
            return;
        }

        // Insert new entry
        var dbInsertStmt = connection.prepareStatement(
                "INSERT INTO containers (world, x, y, z, block) VALUES (?, ?, ?, ?, ?) RETURNING id"
        );
        dbInsertStmt.setString(1, world.getRegistryKey().getValue().toString());
        dbInsertStmt.setInt(2, pos.getX());
        dbInsertStmt.setInt(3, pos.getY());
        dbInsertStmt.setInt(4, pos.getZ());
        dbInsertStmt.setString(5, blockState.getBlock().getTranslationKey());
        var dbInsertRs = dbInsertStmt.executeQuery();
        if (!dbInsertRs.next()) {
            dbInsertStmt.close();
            return;
        }

        var containerId = (UUID) dbInsertRs.getObject("id");

        dbInsertStmt.close();

        var dbItemStmt = connection.prepareStatement(
                "INSERT INTO items (item, count, container) VALUES (?, ?, ?)"
        );
        for (var itemId : items.keySet()) {
            dbItemStmt.clearParameters();
            dbItemStmt.setString(1, itemId);
            dbItemStmt.setInt(2, items.get(itemId));
            dbItemStmt.setObject(3, containerId);
            dbItemStmt.execute();
        }

        dbItemStmt.close();
    }

    private static @NotNull HashMap<String, Integer> tryGetContainerItems(BlockEntity blockEntity) {
        HashMap<String, Integer> items = new HashMap<>();

        LootableContainerBlockEntity container;
        if (blockEntity instanceof ChestBlockEntity chest) {
            container = chest;
        } else if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox) {
            container = shulkerBox;
        } else {
            return items;
        }

        for (int i = 0; i < container.size(); i++) {
            ItemStack stack = container.getStack(i);
            if (!stack.isEmpty()) {
                var itemId = stack.getItem().getTranslationKey();
                items.put(itemId, items.getOrDefault(itemId, 0) + stack.getCount());

                tryGetAsShulkerBoxItems(stack).forEach((k, v) -> {
                    items.put(k, items.getOrDefault(k, 0) + v);
                });

                tryGetAsBundleItems(stack).forEach((k, v) -> {
                    items.put(k, items.getOrDefault(k, 0) + v);
                });
            }
        }
        return items;
    }

    private static @NotNull HashMap<String, Integer> tryGetAsShulkerBoxItems(ItemStack stack) {
        HashMap<String, Integer> items = new HashMap<>();

        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                var containerComponent = stack.get(DataComponentTypes.CONTAINER);
                if (containerComponent != null) {
                    for(var innerStack : containerComponent.stream().toList()) {
                        if (!innerStack.isEmpty()) {
                            var innerItemId = innerStack.getItem().getTranslationKey();
                            items.put(innerItemId, items.getOrDefault(innerItemId, 0) + innerStack.getCount());

                            tryGetAsBundleItems(innerStack).forEach((k, v) -> {
                                items.put(k, items.getOrDefault(k, 0) + v);
                            });
                        }
                    }
                }
            }
        }

        return items;
    }

    private static HashMap<String, Integer> tryGetAsBundleItems(ItemStack stack) {
        HashMap<String, Integer> items = new HashMap<>();

        if (stack.getItem().getTranslationKey().equals("item.minecraft.bundle")) {
            var containerComponent = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (containerComponent != null) {
                for(var innerStack : containerComponent.stream().toList()) {
                    if (!innerStack.isEmpty()) {
                        var innerItemId = innerStack.getItem().getTranslationKey();
                        items.put(innerItemId, items.getOrDefault(innerItemId, 0) + innerStack.getCount());
                    }
                }
            }
        }

        return items;
    }

    public void queueScan(Vec3d location, World world, int radius) {
        instantScanQueue.add((server) -> {
            try {
                Point p1 = new Point((int) (location.getX() - radius), (int) (location.getY() - radius), (int) (location.getZ() - radius));
                Point p2 = new Point((int) (location.getX() + radius), (int) (location.getY() + radius), (int) (location.getZ() + radius));
                AABB aabb = new AABB(p1, p2, world.getRegistryKey().getValue().toString());

                logger.debug("Performing instant scan at {} @ {}", String.format("(%.1f, %.1f, %.1f)", location.getX(), location.getY(), location.getZ()), world.getRegistryKey().getValue().toString());
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
                        World world = null;
                        for (var w : server.getWorlds()) {
                            if (w.getRegistryKey().getValue().equals(Identifier.tryParse(task.getLocation().getWorld()))) {
                                world = w;
                                break;
                            }
                        }

                        scanOne(world, task.getLocation().toBlockPos(), true);
                        logger.debug("Performed delayed scan at {} @ {}", task.getLocation().toString(), task.getLocation().getWorld());
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

    public void removeBlockFromTracking(BlockPos pos, World world) {
        if (connection == null) {
            return;
        }

        try {
            var stmt = connection.prepareStatement(
                    "DELETE FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?"
            );
            stmt.setString(1, world.getRegistryKey().getValue().toString());
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
