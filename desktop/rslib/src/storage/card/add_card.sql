INSERT INTO cards (
    id,
    nid,
    did,
    ord,
    mod,
    usn,
    type,
    queue,
    due,
    ivl,
    factor,
    reps,
    lapses,
    left,
    odue,
    odid,
    flags,
    data
  )
VALUES (
    (
      CASE
        WHEN ?1 IN (
          SELECT id
          FROM cards
        ) THEN (
          SELECT max(id) + 1
          FROM cards
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
    ?,
    ?,
    ?,
    ?,
    ?,
    ?,
    ?,
    ?,
    ?
  )