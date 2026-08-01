SELECT DISTINCT notes.id,
  notes.mid,
  notes.csum,
  CASE
    WHEN cards.odid = 0 THEN cards.did
    ELSE cards.odid
  END AS did
FROM notes
  JOIN cards ON notes.id = cards.nid