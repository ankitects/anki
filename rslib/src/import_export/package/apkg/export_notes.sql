INSERT INTO other.notes
SELECT *
FROM notes
WHERE id IN (
    SELECT DISTINCT nid
    FROM other.cards
  )