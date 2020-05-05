select
  distinct name
from fields
where
  ntid in (
    select
      mid
    from notes
    where
      id in