INSERT INTO active_decks
SELECT id
FROM decks
WHERE name = ?
  OR (
    name >= ?
    AND name < ?
  )