SELECT id
FROM decks
WHERE id IN (
    SELECT id
    FROM active_decks
  )
ORDER BY name