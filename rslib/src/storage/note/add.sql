INSERT INTO notes (
    id,
    guid,
    mid,
    mod,
    usn,
    tags,
    flds,
    sfld,
    csum,
    flags,
    data
  )
VALUES (
    (
      CASE
        WHEN ?1 IN (
          SELECT id
          FROM notes
        ) THEN (
          SELECT max(id) + 1
          FROM notes
        )
        ELSE ?1
      END
    ),
    ?,
    ?,
    ?,
    ?,
    ?,
    ?,
    ?,
    ?,
    0,
    ""
  )