select
  nt.id,
  nt.name,
  count(n.id)
from notetypes nt
left join notes n on (n.mid = nt.id)
group by
  nt.id
order by
  nt.name