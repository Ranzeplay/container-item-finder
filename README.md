# Container Item Finder

A Minecraft mod that helps players locate items within containers using both real-time searching and database-backed tracking.

## Command Guide

The mod provides two main command systems for finding items in containers:

### CIF Commands (Real-time Search)

The `cif` commands perform real-time scanning of containers within a specified range:

#### Search Command

```
/cif search <range> <item> [count]
```

- `range`: The search radius in blocks (integer)
- `item`: The item to search for (e.g., `diamond`, `iron_ingot`)
- `count`: (Optional) The minimum number of items to find (integer)

#### Index Command

```
/cif index <range>
```

- `range`: The search radius in blocks (integer)

This command will scan all containers within the specified range and show a summary of all items found, sorted by total count.

#### Cancel Command

```
/cif cancel
```

Cancels any currently running search or index operation.

### DIF Commands (Database-backed Tracking)

The `dif` commands use a database backend to track containers in predefined areas and provide faster searches:

#### Database Search Command

```
/dif search <item> [count] [range]
```

- `item`: The item to search for (e.g., `diamond`, `iron_ingot`)
- `count`: (Optional) The minimum number of items to find (integer)
- `range`: (Optional) The search radius in blocks (integer)

This command searches the database for tracked containers containing the specified item.

#### Statistics Command

```
/dif stats
```

Shows statistics about the tracking database, including:
- Last scan time and statistics
- Current scanner status (active/inactive)
- Number of containers and items tracked

#### Manual Rescan Command

```
/dif rescan
```

Manually triggers a rescan of all tracking areas (requires operator permissions).

### Examples

#### CIF Commands
- Search for diamonds within 10 blocks: `/cif search 10 diamond`
- Search for 64 iron ingots within 20 blocks: `/cif search 20 iron_ingot 64`
- Index all items in containers within 15 blocks: `/cif index 15`

#### DIF Commands
- Search for diamonds in tracked areas: `/dif search diamond`
- Search for 64 iron ingots: `/dif search iron_ingot 64`
- Check tracking statistics: `/dif stats`
- Manually rescan tracked areas: `/dif rescan`

### Results

**CIF Commands** show:
- The total number of items found
- The number of containers containing the items
- The coordinates of each container
- If a specific count was requested, it will show how many more items are needed

**DIF Commands** show:
- Results from the database of tracked containers
- Faster response times for large areas
- Historical data from the last scan

Unexpected `/cif` and `/dif` command failures are logged on the server console with the full exception stack trace, command text, sender, dimension, and position. This also covers background search and index failures. The sender receives a message directing them to the server console for details; no client-side mod installation is needed for this logging.

## Configuration

The mod uses a config file located at `run/config/cif.json`. Example:

```json
{
  "enableTracking": true,
  "trackingAreas": [
    {
      "p1": { "x": -128, "y": 80, "z": -63 },
      "p2": { "x": -112, "y": 40, "z": -32 },
      "world": "minecraft:overworld"
    }
  ],
  "refreshIntervalMinutes": 3,
  "databaseType": "postgresql",
  "databaseConnectionString": "jdbc:postgresql://localhost:5432/cif?user=postgres&password=postgres",
  "indexThreads": 4
}
```

### Options
- `enableTracking`: Enables/disables container tracking.
- `trackingAreas`: List of areas to track, defined by two points (`p1`, `p2`) and a world name.
- `refreshIntervalMinutes`: How often to refresh tracking data (in minutes).
- `databaseType`: Database backend: `postgresql` (default), `mysql`, `mariadb`, or `sqlite`. Values are case-insensitive. Existing configurations without this entry continue to use PostgreSQL.
- `databaseConnectionString`: JDBC connection string for the database backend.
- `indexThreads`: Number of threads used for indexing containers.

Edit this file to customize mod behavior for your server.

### Database connections

All backends use `databaseConnectionString`, a JDBC connection URL. JDBC drivers are bundled with the mod. Set `enableTracking` to `true` to enable database tracking.

| `databaseType` | Example `databaseConnectionString` |
| --- | --- |
| `postgresql` | `jdbc:postgresql://localhost:5432/cif?user=postgres&password=postgres` |
| `mysql` | `jdbc:mysql://localhost:3306/cif?user=cif&password=secret` |
| `mariadb` | `jdbc:mariadb://localhost:3306/cif?user=cif&password=secret` |
| `sqlite` | `jdbc:sqlite:config/cif.db` |

For PostgreSQL, MySQL, and MariaDB, create the database first and use an account that can create tables. Tables are initialized automatically. SQLite creates the file automatically; relative paths are resolved from the server's working directory, and the parent directory must already exist. Use `jdbc:sqlite:/absolute/path/cif.db` for an absolute path. Changing backends does not transfer existing tracking data; rescan the configured areas to populate the new database.

## Development

### Prerequisites

- Java 25 or higher
- Gradle (optional if using wrapper)

### Building from Source

1. Clone the repository
2. Open a terminal in the project directory
3. Run `./gradlew build`
4. The built mod will be in `build/libs/`

## License

This project is licensed under MIT license.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Contact

For any questions or issues, please open an issue on the GitHub repository.
