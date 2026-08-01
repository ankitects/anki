SELECT id,
  nid,
  due,
  cast(ivl AS integer),
  cast(mod AS integer),
  did,
  odid,
  reps
FROM cards
WHERE did IN (
    SELECT id
    FROM active_decks
  )
  AND (
    queue = ?
    AND due <= ?
  )