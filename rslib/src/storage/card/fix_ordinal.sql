UPDATE cards
SET ord = max(0, min(30000, ord)),
  mod = ?1,
  usn = ?2
WHERE ord != max(0, min(30000, ord))