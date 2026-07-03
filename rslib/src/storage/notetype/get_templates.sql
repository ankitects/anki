SELECT ord,
  name,
  mtime_secs,
  usn,
  config
FROM templates
WHERE ntid = ?
ORDER BY ord