select
  count(id) = 0
from cards
where
  nid = ?;