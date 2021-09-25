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
      CASE
        :sched_ver
        WHEN 2 THEN (
          -- v2 scheduler
          (
            queue = :learn_queue
            AND due < :learn_cutoff
          )
          OR (
            queue = :preview_queue
            AND due <= :learn_cutoff
          )
        )
        ELSE (
          -- v1 scheduler
          CASE
            WHEN queue = :learn_queue
            AND due < :learn_cutoff THEN left / 1000
            ELSE 0
          END
        )
      END
    )
  ),
  -- total
  COUNT(1)
FROM cards