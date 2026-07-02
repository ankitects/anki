SELECT DISTINCT name
FROM fields
WHERE ntid IN (
    SELECT mid
    FROM notes
    WHERE id IN