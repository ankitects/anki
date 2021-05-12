SELECT id,
  nid,
  due,
  ord,
  cast(mod AS integer),
  odid
FROM cards
WHERE did = ?
  AND queue = 0