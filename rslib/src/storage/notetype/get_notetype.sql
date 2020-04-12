select
  name,
  mtime_secs,
  usn,
  config
from notetypes
where
  id = ?