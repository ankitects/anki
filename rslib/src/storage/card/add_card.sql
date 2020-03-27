insert into cards (
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
values
  (
    (
      case
        when ?1 in (
          select
            id
          from cards
        ) then (
          select
            max(id) + 1
          from cards
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