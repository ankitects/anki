INSERT INTO other.notetypes
SELECT *
FROM notetypes
WHERE id IN (
    SELECT DISTINCT mid
    FROM other.notes
  )