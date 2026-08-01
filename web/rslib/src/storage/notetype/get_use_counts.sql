SELECT nt.id,
  nt.name,
  (
    SELECT COUNT(*)
    FROM notes n
    WHERE nt.id = n.mid
  )
FROM notetypes nt
ORDER BY nt.name