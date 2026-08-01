SELECT id,
  cid,
  usn,
  ease,
  cast(ivl AS integer),
  cast(lastIvl AS integer),
  factor,
  time,
  type,
  reveal_millis,
  data
FROM revlog