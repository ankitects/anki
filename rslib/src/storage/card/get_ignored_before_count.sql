SELECT COUNT(DISTINCT cid)
FROM revlog
WHERE id > ?
  AND type == 0
  AND cid IN search_cids