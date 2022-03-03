SELECT id,
  nid,
  ord,
  cast(mod AS integer),
  did,
  odid
FROM cards
WHERE did IN (
    SELECT id
    FROM active_decks
  )
  AND queue = 0