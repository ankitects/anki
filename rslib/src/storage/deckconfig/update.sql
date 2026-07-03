UPDATE deck_config
SET name = ?,
  mtime_secs = ?,
  usn = ?,
  config = ?
WHERE id = ?;