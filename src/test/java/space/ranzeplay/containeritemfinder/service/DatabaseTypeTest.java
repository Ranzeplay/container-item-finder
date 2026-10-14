package space.ranzeplay.containeritemfinder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTypeTest {
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
        try (var statement = connection.prepareStatement(DatabaseType.SQLITE.containerUpsertSql())) {
            DatabaseType.SQLITE.bindId(statement, 1, id);
            statement.setString(2, "minecraft:overworld");
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
