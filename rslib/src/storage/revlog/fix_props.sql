UPDATE revlog
SET ivl = min(max(round(ivl), -2147483648), 2147483647),
  lastIvl = min(max(round(lastIvl), -2147483648), 2147483647),
  time = min(max(round(time), 0), 2147483647),
  type = (
    CASE
      WHEN type = 0
      AND time = 0
      AND ease = 0 THEN 5
      ELSE type
    END
  )
WHERE ivl != min(max(round(ivl), -2147483648), 2147483647)
  OR lastIvl != min(max(round(lastIvl), -2147483648), 2147483647)
  OR time != min(max(round(time), 0), 2147483647)
  OR type != (
    CASE
      WHEN type = 0
      AND time = 0
      AND ease = 0 THEN 5
      ELSE type
    END
  )