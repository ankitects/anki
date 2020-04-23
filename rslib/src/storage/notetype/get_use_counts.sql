select
  mid,
  (
    select
      name
    from notetypes nt
    where
      nt.id = mid
  ) as name,
  count(id)
from notes
group by
  mid
order by
  name