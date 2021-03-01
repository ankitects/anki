SELECT id,
  nid,
  due,
  ord,
  cast(mod AS integer)
FROM cards
WHERE did = ?
  AND queue = 0