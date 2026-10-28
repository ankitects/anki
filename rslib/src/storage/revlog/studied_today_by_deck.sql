SELECT CASE
    WHEN c.odid == 0 THEN c.did
    ELSE c.odid
  END AS original_did,
  COUNT(DISTINCT r.cid) AS cnt
FROM revlog AS r
  JOIN cards AS c ON r.cid = c.id
WHERE r.id > ?
  AND r.ease > 0
  AND (
    r.type < 3
    OR r.factor != 0
  )
GROUP BY original_did