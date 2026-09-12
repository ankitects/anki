SELECT count()
FROM revlog
WHERE cid = ?1
  AND id >= ?2
  AND ease = 1
  AND type != ?3
  AND type != ?4
