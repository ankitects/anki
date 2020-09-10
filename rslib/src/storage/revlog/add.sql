insert
  or ignore into revlog (
    id,
    cid,
    usn,
    ease,
    ivl,
    lastIvl,
    factor,
    time,
    type
  )
values (
    (
      case
        when ?1 in (
          select id
          from revlog
        ) then (
          select max(id) + 1
          from revlog
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
    ?
  )