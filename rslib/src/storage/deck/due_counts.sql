SELECT did,
  sum(queue = :new_queue),
  sum(
    queue = :review_queue
    AND due <= :day_cutoff
  ),
  -- learning
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
            queue = :daylearn_queue
            AND due <= :day_cutoff
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
            WHEN queue = :daylearn_queue
            AND due <= :day_cutoff THEN 1
            ELSE 0
          END
        )
      END
    )
  )
FROM cards
WHERE queue >= 0