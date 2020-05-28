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
values
  (?, ?, ?, ?, ?, ?, ?, ?, ?)