UPDATE cards
SET due = (
    CASE
      WHEN type = 0
      AND queue != 4 THEN 1000000 + due % 1000000
      ELSE due
    END
  ),
  mod = ?1,
  usn = ?2
WHERE due != (
    CASE
      WHEN type = 0
      AND queue != 4 THEN 1000000 + due % 1000000
      ELSE due
    END
  )
  AND due >= 1000000;