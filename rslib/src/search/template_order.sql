DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  ntid integer NOT NULL,
  ord integer NOT NULL,
  UNIQUE(ntid, ord)
);
INSERT INTO sort_order (ntid, ord)
SELECT ntid,
  ord
FROM templates
ORDER BY name