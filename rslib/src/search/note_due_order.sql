DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  nid integer NOT NULL UNIQUE
);
INSERT INTO sort_order (nid)
SELECT nid
FROM cards
WHERE (
    odid = 0
    AND type != 0
    AND queue > 0
  )
GROUP BY nid