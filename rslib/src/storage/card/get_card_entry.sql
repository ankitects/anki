SELECT cards.id,
  cards.nid,
  CASE
    WHEN cards.odid = 0 THEN cards.did
    ELSE cards.odid
  END AS did,
  cards.ord,
  notes.mid
FROM cards
  JOIN notes ON notes.id = cards.nid;