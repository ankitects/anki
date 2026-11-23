WITH reviewed_today AS (
  SELECT DISTINCT cid
  FROM revlog
  WHERE id >= ?1
    AND id < ?2
    AND ease BETWEEN 1 AND 4
    AND (
      type != 3
      OR factor != 0
    )
)
SELECT cards.did,
  cards.queue,
  COUNT(*)
FROM reviewed_today
  JOIN cards ON cards.id = reviewed_today.cid
WHERE cards.queue IN (2, 3)
  AND cards.due <= ?3
GROUP BY cards.did,
  cards.queue