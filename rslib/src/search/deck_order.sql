DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  did integer NOT NULL UNIQUE
);
INSERT INTO sort_order (did)
SELECT id
FROM decks
ORDER BY name;