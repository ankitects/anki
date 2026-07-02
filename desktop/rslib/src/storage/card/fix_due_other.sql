UPDATE cards
SET due = (
    CASE
      WHEN queue = 2
      AND due > 100000 THEN ?1
      ELSE min(max(round(due), -2147483648), 2147483647)
    END
  ),
  mod = ?2,
  usn = ?3
WHERE due != (
    CASE
      WHEN queue = 2
      AND due > 100000 THEN ?1
      ELSE min(max(round(due), -2147483648), 2147483647)
    END
  );