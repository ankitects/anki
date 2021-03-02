INSERT INTO search_cids
SELECT id
FROM cards
WHERE id != ?
  AND nid = ?
  AND (
    (
      ?
      AND queue = ?
    )
    OR (
      ?
      AND queue = ?
    )
  );