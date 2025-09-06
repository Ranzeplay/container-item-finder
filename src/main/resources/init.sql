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

CREATE OR REPLACE FUNCTION distance(
    x1 double precision, y1 double precision, z1 double precision,
    x2 double precision, y2 double precision, z2 double precision
) RETURNS double precision AS
$$
BEGIN
    RETURN sqrt(
            pow(x2 - x1, 2) +
            pow(y2 - y1, 2) +
            pow(z2 - z1, 2)
           );
END;
$$ LANGUAGE plpgsql IMMUTABLE
                    STRICT;
