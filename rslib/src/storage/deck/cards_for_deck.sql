SELECT id
FROM cards
WHERE did = ?1
  OR (
    odid != 0
    AND odid = ?1
  )