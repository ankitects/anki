SELECT queue,
  id,
  nid,
  due,
  cast(ivl AS integer),
  cast(mod AS integer)
FROM cards
WHERE did = ?1
  AND (
    (
      queue IN (2, 3)
      AND due <= ?2
    )
    OR (
      queue IN (1, 4)
      AND due <= ?3
    )
  )