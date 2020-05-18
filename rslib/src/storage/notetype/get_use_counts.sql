select
  nt.id,
  nt.name,
  (
    select
      count(*)
    from notes n
    where
      nt.id = n.mid
  )
from notetypes nt
order by
  nt.name