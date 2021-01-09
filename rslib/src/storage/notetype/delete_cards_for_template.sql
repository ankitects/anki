DELETE FROM cards
WHERE nid IN (
    SELECT id
    FROM notes
    WHERE mid = ?
  )
  AND ord = ?;