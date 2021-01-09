SELECT ord,
  name,
  config
FROM FIELDS
WHERE ntid = ?
ORDER BY ord