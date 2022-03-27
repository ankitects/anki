INSERT INTO other.revlog
SELECT *
FROM revlog
WHERE cid IN (
    SELECT cid
    FROM other.cards
  )