INSERT INTO other.cards
SELECT (
    id,
    nid,
    :did,
    ord,
    mod,
    usn,
    type,
    queue,
    due,
    ivl,
    factor,
    reps,
    lapses,
    left,
    odue,
    odid,
    flags,
    data
  )
FROM cards
WHERE id NOT IN (
    SELECT id
    FROM other.cards
  )
  AND nid IN (
    SELECT DISTINCT nid
    FROM other.cards
  )