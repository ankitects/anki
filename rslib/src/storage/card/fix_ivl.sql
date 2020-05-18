update cards
set
  ivl = min(max(round(ivl), 0), 2147483647),
  mod = ?1,
  usn = ?2
where
  ivl != min(max(round(ivl), 0), 2147483647)