DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  nid integer NOT NULL UNIQUE
);
INSERT INTO sort_order (nid)
SELECT nid
FROM cards
WHERE type IN (2, 3)
GROUP BY nid
ORDER BY AVG(ivl);