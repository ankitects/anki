SELECT id,
  nid,
  CASE
    WHEN odid = 0 THEN did
    ELSE odid
  END AS did,
  ord
FROM cards;
