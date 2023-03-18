INSERT INTO theme (id, name, mtime_secs, usn, vars)
VALUES (
    (
      CASE
        WHEN ?1 IN (
          SELECT id
          FROM theme
        ) THEN (
          SELECT max(id) + 1
          FROM theme
        )
        ELSE ?1
      END
    ),
    ?,
    ?,
    ?,
    ?
  );