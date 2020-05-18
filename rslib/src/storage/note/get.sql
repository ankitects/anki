select
  guid,
  mid,
  mod,
  usn,
  tags,
  flds
from notes
where
  id = ?