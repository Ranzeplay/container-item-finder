CREATE TABLE IF NOT EXISTS containers
(
    id TEXT PRIMARY KEY NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    world TEXT NOT NULL,
    block TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS items
(
    id TEXT PRIMARY KEY NOT NULL,
    item TEXT NOT NULL,
    count INT NOT NULL,
    container TEXT REFERENCES containers (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS containers_x_y_z_key ON containers (x, y, z);
