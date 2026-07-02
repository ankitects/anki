DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  nid integer NOT NULL UNIQUE
);
INSERT INTO sort_order (nid)
SELECT nid
FROM cards
GROUP BY nid
ORDER BY COALESCE(
    extract_original_position(data),
    CASE
      WHEN type == 0 THEN due
      ELSE 0
    END
  );