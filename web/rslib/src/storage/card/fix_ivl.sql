UPDATE cards
SET ivl = min(max(round(ivl), 0), 2147483647),
  mod = ?1,
  usn = ?2
WHERE ivl != min(max(round(ivl), 0), 2147483647)