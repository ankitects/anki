SELECT id,
  nid,
  ord,
  -- original deck
  (
    CASE
      odid
      WHEN 0 THEN did
      ELSE odid
    END
  ),
  -- new position if card is empty
  (
    CASE
      type
      WHEN 0 THEN (
        CASE
          odue
          WHEN 0 THEN max(0, due)
          ELSE max(odue, 0)
        END
      )
      ELSE NULL
    END
  )
FROM cards c