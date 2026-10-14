package space.ranzeplay.containeritemfinder.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public enum DatabaseType {
    POSTGRESQL("org.postgresql.Driver", "jdbc:postgresql:", "init.sql"),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql:", "init-mysql.sql"),
    MARIADB("org.mariadb.jdbc.Driver", "jdbc:mariadb:", "init-mysql.sql"),
    SQLITE("org.sqlite.JDBC", "jdbc:sqlite:", "init-sqlite.sql");

    private final String driver;
    private final String urlPrefix;
    private final String schemaResource;

    DatabaseType(String driver, String urlPrefix, String schemaResource) {
        this.driver = driver;
        this.urlPrefix = urlPrefix;
        this.schemaResource = schemaResource;
    }

    public static DatabaseType fromConfig(String value) {
        // Configurations written before databaseType was introduced use PostgreSQL.
        if (value == null) {
            return POSTGRESQL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported databaseType; expected postgresql, mysql, mariadb or sqlite", e);
        }
    }

    public Connection connect(String url) throws SQLException {
        if (url == null || !url.startsWith(urlPrefix)) {
            throw new IllegalArgumentException("databaseConnectionString must start with " + urlPrefix + " for " + name());
        }
        try {
            // Fabric's nested JAR class loader need not discover JDBC service providers.
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not available for " + name(), e);
        }
        Connection connection = DriverManager.getConnection(url);
        try {
            initialize(connection);
            return connection;
        } catch (Exception e) {
            connection.close();
            throw new SQLException("Failed to initialize database schema for " + name(), e);
        }
    }

    private void initialize(Connection connection) throws Exception {
        try (var stream = DatabaseType.class.getClassLoader().getResourceAsStream(schemaResource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing database schema: " + schemaResource);
            }
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (var statement = connection.createStatement()) {
                if (this == SQLITE) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    statement.execute("PRAGMA busy_timeout = 5000");
                }
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) {
                        statement.execute(command);
                    }
                }
            }
        }
    }

    public String containerUpsertSql() {
        String insert = "INSERT INTO containers (id, world, x, y, z, block) VALUES (?, ?, ?, ?, ?, ?) ";
        if (this == MYSQL || this == MARIADB) {
            return insert + "ON DUPLICATE KEY UPDATE world = VALUES(world), block = VALUES(block)";
        }
        return insert + "ON CONFLICT (x, y, z) DO UPDATE SET world = EXCLUDED.world, block = EXCLUDED.block";
    }

    public void bindId(PreparedStatement statement, int index, UUID id) throws SQLException {
        if (this == POSTGRESQL) {
            statement.setObject(index, id);
        } else {
            statement.setString(index, id.toString());
        }
    }

    public static String searchSql(boolean limitedRange, boolean nearestFirst) {
        // Squared distances avoid dialect-specific functions (including SQLite math extensions).
        return """
                SELECT * FROM (
                    SELECT items.count, containers.world, containers.x, containers.y, containers.z,
                        (containers.x - center.x) * (containers.x - center.x)
                        + (containers.y - center.y) * (containers.y - center.y)
                        + (containers.z - center.z) * (containers.z - center.z) AS distance_squared
                    FROM items JOIN containers ON items.container = containers.id
                    CROSS JOIN (SELECT ? AS x, ? AS y, ? AS z) center
                    WHERE items.item = ? AND containers.world = ?
                ) sub
                """ + (limitedRange ? "WHERE sub.distance_squared <= ? " : "")
                + "ORDER BY sub.distance_squared " + (nearestFirst ? "ASC" : "DESC");
    }
}
