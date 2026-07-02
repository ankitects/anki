SELECT ord,
  name,
  config
FROM fields
WHERE ntid = ?
ORDER BY ord