UPDATE cards
SET odue = (
    CASE
      WHEN odue > 0
      AND (
        type = 1
        OR queue = 2
      )
      AND NOT ?3
      AND NOT odid THEN 0
      ELSE min(max(round(odue), -2147483648), 2147483647)
    END
  ),
  mod = ?1,
  usn = ?2
WHERE odue != (
    CASE
      WHEN odue > 0
      AND (
        type = 1
        OR queue = 2
      )
      AND NOT ?3
      AND NOT odid THEN 0
      ELSE min(max(round(odue), -2147483648), 2147483647)
    END
  );