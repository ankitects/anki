SELECT DISTINCT COUNT(cid)
FROM revlog
WHERE id > ?
  AND type == 0