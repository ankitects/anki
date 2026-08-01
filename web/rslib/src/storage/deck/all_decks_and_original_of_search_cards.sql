WITH cids AS (
  SELECT cid
  FROM search_cids
)
SELECT did
FROM cards
WHERE id IN cids
UNION
SELECT odid
FROM cards
WHERE odid != 0
  AND id IN cids