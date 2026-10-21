CREATE TABLE IF NOT EXISTS containers
(
    id    UUID PRIMARY KEY NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    x     INT              NOT NULL,
    y     int              NOT NULL,
    z     int              NOT NULL,
    world TEXT             NOT NULL,
    block TEXT             NOT NULL
);

CREATE TABLE IF NOT EXISTS items
(
    id        UUID PRIMARY KEY NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    item      TEXT             NOT NULL,
    count     INT              NOT NULL,

    container UUID REFERENCES containers (id) ON DELETE CASCADE
);

-- Coordinates are unique within each dimension.
-- Keep this non-concurrent because startup executes the script as a single JDBC statement.
CREATE UNIQUE INDEX IF NOT EXISTS containers_world_x_y_z_key
    ON containers (world, x, y, z);
