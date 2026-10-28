SELECT id,
  nid,
  ord,
  cast(mod AS integer),
  did,
  odid
FROM cards
WHERE did = ?
  AND queue = 0