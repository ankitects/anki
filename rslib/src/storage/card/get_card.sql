-- the casts are required as Anki didn't prevent add-ons from
-- storing strings or floats in columns before
select
  nid,
  did,
  ord,
  cast(mod as integer),
  usn,
  type,
  queue,
  due,
  cast(ivl as integer),
  factor,
  reps,
  lapses,
  left,
  odue,
  odid,
  flags,
  data
from cards
where
  id = ?