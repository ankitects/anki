SELECT
  id,
  nid,
  did,
  ord,
  cast(mod AS integer),
  usn,
  type,
  queue,
  due,
  cast(ivl AS integer),
  factor,
  reps,
  lapses,
  LEFT,
  odue,
  odid,
  flags,
  data
FROM
  cards