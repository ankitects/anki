SELECT id,
  nid,
  due,
  cast(ivl AS integer),
  cast(mod AS integer),
  did,
  odid,
  reps
FROM cards
WHERE id IN CARD_IDS
  AND did IN (
    SELECT id
    FROM active_decks
  )
  AND queue = ?