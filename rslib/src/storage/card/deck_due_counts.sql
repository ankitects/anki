SELECT CASE
    WHEN odid == 0 THEN did
    ELSE odid
  END AS original_did,
  CASE
    WHEN odid == 0 THEN due
    ELSE odue
  END AS true_due,
  COUNT() AS COUNT
FROM cards
WHERE type = 2
  AND queue != -1
GROUP BY original_did,
  true_due