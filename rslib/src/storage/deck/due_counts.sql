SELECT did,
  -- new
  sum(queue = :new_queue),
  -- reviews
  sum(
    queue = :review_queue
    AND due <= :day_cutoff
  ),
  -- interday learning
  sum(
    queue = :daylearn_queue
    AND due <= :day_cutoff
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