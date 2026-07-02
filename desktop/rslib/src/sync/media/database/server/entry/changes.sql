SELECT fname,
  usn,
  (
    CASE
      WHEN size > 0 THEN lower(hex(csum))
      ELSE ''
    END
  )
FROM media
WHERE usn > ?
LIMIT 1000