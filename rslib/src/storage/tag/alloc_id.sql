SELECT CASE
    WHEN ?1 IN (
      SELECT id
      FROM tags
    ) THEN (
      SELECT max(id) + 1
      FROM tags
    )
    ELSE ?1
  END;