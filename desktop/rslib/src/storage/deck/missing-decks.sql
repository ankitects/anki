SELECT DISTINCT did
FROM cards
WHERE did NOT IN (
    SELECT id
    FROM decks
  );