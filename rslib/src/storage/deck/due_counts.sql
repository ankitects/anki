WITH reviewed_today AS (
  SELECT DISTINCT cid
  FROM revlog
  WHERE id >= :review_day_start
    AND id < :review_day_end
    AND ease BETWEEN 1 AND 4
    AND (
      type != 3
      OR factor != 0
    )
)
SELECT cards.did,
  -- new
  sum(queue = :new_queue),
  -- reviews
  sum(
    queue = :review_queue
    AND due <= :day_cutoff
  ),
  -- reviews that were already answered this scheduler day
  sum(
    queue = :review_queue
    AND due <= :day_cutoff
    AND reviewed_today.cid IS NOT NULL
  ),
  -- interday learning
  sum(
    queue = :daylearn_queue
    AND due <= :day_cutoff
  ),
  -- interday learning that was already answered this scheduler day
  sum(
    queue = :daylearn_queue
    AND due <= :day_cutoff
    AND reviewed_today.cid IS NOT NULL
  ),
  -- intraday learning
  sum(
    (
      (
        queue = :learn_queue
        AND due < :learn_cutoff
      )
      OR (
        queue = :preview_queue
        AND due <= :learn_cutoff
      )
    )
  ),
  -- total
  COUNT(1)
FROM cards
  LEFT JOIN reviewed_today ON reviewed_today.cid = cards.id