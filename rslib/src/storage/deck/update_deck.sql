UPDATE decks
SET name = ?,
  mtime_secs = ?,
  usn = ?,
  common = ?,
  kind = ?
WHERE id = ?