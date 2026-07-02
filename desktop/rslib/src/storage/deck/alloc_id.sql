SELECT CASE
    WHEN ?1 IN (
      SELECT id
      FROM decks
    ) THEN (
      SELECT max(id) + 1
      FROM decks
    )
    ELSE ?1
  END;