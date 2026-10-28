UPDATE cards
SET
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
  LEFT = ?,
  odue = ?,
  odid = ?,
  flags = ?,
  data = ?
WHERE
  id = ?