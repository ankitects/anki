SELECT coalesce(max(ord), 0)
FROM cards
WHERE nid IN (
    SELECT id
    FROM notes
    WHERE mid = ?
  )