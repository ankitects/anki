select
  ord,
  name,
  mtime_secs,
  usn,
  config
from templates
where
  ntid = ?
order by
  ord