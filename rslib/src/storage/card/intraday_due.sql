SELECT id,
  nid,
  due,
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
    queue IN (1, 4)
    AND due <= ?
  )