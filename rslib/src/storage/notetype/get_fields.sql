select
  ord,
  name,
  config
from fields
where
  ntid = ?
order by
  ord