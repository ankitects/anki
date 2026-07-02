INSERT INTO search_cids
SELECT id
FROM cards
WHERE nid IN (
    SELECT nid
    FROM search_nids
  )