SELECT COUNT(),
  coalesce(sum(time) / 1000.0, 0.0)
FROM revlog
WHERE id > ?
  AND type != ?
  AND type != ?