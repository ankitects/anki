update cards
set
  nid = ?,
  did = ?,
  ord = ?,
  mod = ?,
  usn = ?,
  type = ?,
  queue = ?,
  due = ?,
  ivl = ?,
  factor = ?,
  reps = ?,
  lapses = ?,
  left = ?,
  odue = ?,
  odid = ?,
  flags = ?,
  data = ?
where
  id = ?