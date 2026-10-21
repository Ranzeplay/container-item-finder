package space.ranzeplay.containeritemfinder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTypeTest {
    @Test
    void sqliteKeepsDimensionsSeparate() throws Exception {
        try (var connection = DatabaseType.SQLITE.connect("jdbc:sqlite::memory:")) {
            assertDimensionIsolation(connection);
        }
    }

    @Test
    void sqliteUpgradesLegacyCoordinateIndexWithoutLosingRecords(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("legacy.db");
        UUID legacyId = UUID.randomUUID();
        try (var connection = DatabaseType.SQLITE.connect(url)) {
            insertContainer(connection, legacyId, "ResourceKey[minecraft:root / minecraft:dimension]", 3, 4, 0, "chest");
            insertItem(connection, legacyId, 99);
            try (var statement = connection.createStatement()) {
                statement.execute("DROP INDEX containers_world_x_y_z_key");
                statement.execute("CREATE UNIQUE INDEX containers_x_y_z_key ON containers (x, y, z)");
            }
        }
        try (var connection = DatabaseType.SQLITE.connect(url)) {
            assertDimensionIsolation(connection);
            try (var statement = connection.prepareStatement("SELECT count FROM items WHERE container = ?")) {
                DatabaseType.SQLITE.bindId(statement, 1, legacyId);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(99, result.getInt(1));
                }
            }
        }
        // Initialization and migration are safe to repeat on the upgraded database.
        try (var connection = DatabaseType.SQLITE.connect(url);
             var indexes = connection.getMetaData().getIndexInfo(null, null, "containers", true, false)) {
            while (indexes.next()) {
                assertNotEquals("containers_x_y_z_key", indexes.getString("INDEX_NAME"));
            }
        }
    }

    private void assertDimensionIsolation(Connection connection) throws Exception {
        UUID overworld = UUID.randomUUID();
        UUID nether = UUID.randomUUID();
        insertContainer(connection, overworld, "minecraft:overworld", 3, 4, 0, "chest");
        insertContainer(connection, nether, "minecraft:the_nether", 3, 4, 0, "chest");
        // A repeated scan updates only its own dimension, keeping the existing ID/items.
        insertContainer(connection, UUID.randomUUID(), "minecraft:the_nether", 3, 4, 0, "shulker_box");
        insertItem(connection, overworld, 64);
        insertItem(connection, nether, 32);
        for (var expected : Map.of("minecraft:overworld", 64, "minecraft:the_nether", 32).entrySet()) {
            try (var statement = connection.prepareStatement(DatabaseType.searchSql(false, false))) {
                statement.setDouble(1, 0);
                statement.setDouble(2, 0);
                statement.setDouble(3, 0);
                statement.setString(4, "diamond");
                statement.setString(5, expected.getKey());
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(expected.getValue().intValue(), result.getInt("count"));
                    assertFalse(result.next());
                }
            }
        }
        try (var statement = connection.prepareStatement("DELETE FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, "minecraft:the_nether");
            statement.setInt(2, 3);
            statement.setInt(3, 4);
            statement.setInt(4, 0);
            assertEquals(1, statement.executeUpdate());
        }
        assertSearch(connection, false, false, 0, 64, 25);
    }

    @Test
    void sqliteFilePersistsAndSchemaCanBeInitializedAgain(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("cif.db");
        try (var connection = DatabaseType.SQLITE.connect(url)) {
            insertContainer(connection, UUID.randomUUID(), 1, 2, 3, "chest");
        }
        try (var connection = DatabaseType.SQLITE.connect(url);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM containers")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void parsesTypesAndPreservesExistingConfigurations() {
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromConfig(null));
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromConfig("MySQL"));
        assertEquals(DatabaseType.MARIADB, DatabaseType.fromConfig("mariadb"));
        assertEquals(DatabaseType.SQLITE, DatabaseType.fromConfig(" sqlite "));
        assertThrows(IllegalArgumentException.class, () -> DatabaseType.fromConfig("unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseType.SQLITE.connect("jdbc:mysql://localhost/test"));
    }

    @Test
    void sqliteSupportsUpsertsSearchAndCascadingDeletes() throws Exception {
        try (var connection = DatabaseType.SQLITE.connect("jdbc:sqlite::memory:")) {
            UUID id = UUID.randomUUID();
            insertContainer(connection, id, 3, 4, 0, "chest");
            insertContainer(connection, UUID.randomUUID(), 3, 4, 0, "shulker_box");
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT * FROM containers")) {
                assertTrue(result.next());
                assertEquals(id.toString(), result.getString("id"));
                assertEquals("shulker_box", result.getString("block"));
                assertFalse(result.next());
            }
            insertItem(connection, id, 64);
            UUID farther = UUID.randomUUID();
            insertContainer(connection, farther, 6, 8, 0, "chest");
            insertItem(connection, farther, 32);

            assertSearch(connection, true, true, 25, 64, 25);
            assertSearch(connection, false, true, 0, 64, 25);
            assertSearch(connection, false, false, 0, 32, 100);

            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM containers");
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM items")) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }
            }
        }
    }

    private void insertContainer(Connection connection, UUID id, int x, int y, int z, String block) throws Exception {
        insertContainer(connection, id, "minecraft:overworld", x, y, z, block);
    }

    private void insertContainer(Connection connection, UUID id, String world, int x, int y, int z, String block) throws Exception {
        try (var statement = connection.prepareStatement(DatabaseType.SQLITE.containerUpsertSql())) {
            DatabaseType.SQLITE.bindId(statement, 1, id);
            statement.setString(2, world);
            statement.setInt(3, x);
            statement.setInt(4, y);
            statement.setInt(5, z);
            statement.setString(6, block);
            statement.executeUpdate();
        }
    }

    private void insertItem(Connection connection, UUID container, int count) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO items (id, item, count, container) VALUES (?, ?, ?, ?)")) {
            DatabaseType.SQLITE.bindId(statement, 1, UUID.randomUUID());
            statement.setString(2, "diamond");
            statement.setInt(3, count);
            DatabaseType.SQLITE.bindId(statement, 4, container);
            statement.executeUpdate();
        }
    }

    private void assertSearch(Connection connection, boolean limited, boolean nearest, double rangeSquared,
                              int expectedCount, double expectedDistanceSquared) throws Exception {
        try (var statement = connection.prepareStatement(DatabaseType.searchSql(limited, nearest))) {
            statement.setDouble(1, 0);
            statement.setDouble(2, 0);
            statement.setDouble(3, 0);
            statement.setString(4, "diamond");
            statement.setString(5, "minecraft:overworld");
            if (limited) {
                statement.setDouble(6, rangeSquared);
            }
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expectedCount, result.getInt("count"));
                assertEquals(expectedDistanceSquared, result.getDouble("distance_squared"));
                if (limited) {
                    assertFalse(result.next());
                }
            }
        }
    }
}
