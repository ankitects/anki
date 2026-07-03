DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  ntid integer NOT NULL UNIQUE
);
INSERT INTO sort_order (ntid)
SELECT id
FROM notetypes
ORDER BY name;