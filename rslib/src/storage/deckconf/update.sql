update deck_config
set
  name = ?,
  mtime_secs = ?,
  usn = ?,
  config = ?
where
  id = ?;