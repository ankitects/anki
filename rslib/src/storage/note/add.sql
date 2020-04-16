insert into notes (
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
    (
      case
        when ?1 in (
          select
            id
          from notes
        ) then (
          select
            max(id) + 1
          from notes
        )
        else ?1
      end
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