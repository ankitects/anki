INSERT
  OR REPLACE INTO notes (
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
    ?,
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