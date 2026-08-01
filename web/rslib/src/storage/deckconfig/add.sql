INSERT INTO deck_config (id, name, mtime_secs, usn, config)
VALUES (
    (
      CASE
        WHEN ?1 IN (
          SELECT id
          FROM deck_config
        ) THEN (
          SELECT max(id) + 1
          FROM deck_config
        )
        ELSE ?1
      END
    ),
    ?,
    ?,
    ?,
    ?
  );