INSERT INTO search_cids
SELECT id
FROM cards
WHERE due >= ?
  AND type = ?