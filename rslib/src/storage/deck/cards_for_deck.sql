select
  id
from cards
where
  did = ?1
  or (
    odid != 0
    and odid = ?1
  )