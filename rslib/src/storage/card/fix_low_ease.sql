UPDATE cards
SET factor = 2500,
  usn = ?
WHERE factor != 0
  AND factor <= 2000
  AND (
    did IN DECK_IDS
    OR odid IN DECK_IDS
  )