insert
  or replace into notes (
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
values
  (
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