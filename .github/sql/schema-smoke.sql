-- Use explicit UUID strings so the same SQL works on every backend.
INSERT INTO containers (id, x, y, z, world, block)
VALUES ('00000000-0000-0000-0000-000000000001', 1, 2, 3, 'minecraft:overworld', 'chest');

INSERT INTO items (id, item, count, container)
VALUES ('00000000-0000-0000-0000-000000000002', 'diamond', 64, '00000000-0000-0000-0000-000000000001');

SELECT COUNT(*) FROM containers;
SELECT SUM(count) FROM items;

-- Deleting the container must also delete its items through the foreign key.
DELETE FROM containers WHERE id = '00000000-0000-0000-0000-000000000001';
SELECT COUNT(*) FROM items;
