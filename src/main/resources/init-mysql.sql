CREATE TABLE IF NOT EXISTS containers
(
    id CHAR(36) PRIMARY KEY NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    world TEXT NOT NULL,
    block TEXT NOT NULL,
    UNIQUE KEY containers_x_y_z_key (x, y, z)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS items
(
    id CHAR(36) PRIMARY KEY NOT NULL,
    item TEXT NOT NULL,
    count INT NOT NULL,
    container CHAR(36),
    FOREIGN KEY (container) REFERENCES containers (id) ON DELETE CASCADE
) ENGINE=InnoDB;
