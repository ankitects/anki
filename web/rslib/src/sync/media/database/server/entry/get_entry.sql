SELECT fname,
  csum,
  size,
  usn,
  mtime
FROM media
WHERE fname = ?;