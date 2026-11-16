SELECT id,
  nid,
  did,
  CAST(mod AS integer),
  queue,
  due,
  CAST(ivl AS integer),
  reps,
  odue,
  odid,
  data
FROM cards