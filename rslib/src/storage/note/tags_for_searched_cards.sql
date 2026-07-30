SELECT id,
  tags
FROM notes
WHERE id IN (
    SELECT nid
    FROM cards
    WHERE id IN (SELECT cid FROM search_cids)
  )
