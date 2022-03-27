INSERT INTO other.cards
SELECT *
FROM cards
WHERE did IN (
    SELECT did
    FROM other.decks
  )