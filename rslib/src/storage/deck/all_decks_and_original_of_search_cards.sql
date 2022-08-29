WITH cids AS (
  SELECT cid
  FROM search_cids
)
SELECT DISTINCT did
FROM cards
WHERE id IN cids
UNION
SELECT DISTINCT odid
FROM cards
WHERE odid != 0
  AND id IN cids