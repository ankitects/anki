INSERT
  OR IGNORE INTO deck_config (id, name, mtime_secs, usn, config)
VALUES (?, ?, ?, ?, ?);