SELECT nid,
  did
FROM cards
WHERE ord = 0
  AND nid IN (
    SELECT nid
    FROM search_nids
  )