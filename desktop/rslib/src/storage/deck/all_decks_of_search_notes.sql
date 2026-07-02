SELECT nid,
  did
FROM cards
WHERE nid IN (
    SELECT nid
    FROM search_nids
  )
GROUP BY nid
HAVING ord = MIN(ord)