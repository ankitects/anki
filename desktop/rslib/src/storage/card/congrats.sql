SELECT coalesce(
    sum(
      queue IN (:review_queue, :day_learn_queue)
      AND due <= :today
    ),
    0
  ) AS review_count,
  coalesce(sum(queue = :new_queue), 0) AS new_count,
  coalesce(sum(queue = :sched_buried_queue), 0) AS sched_buried,
  coalesce(sum(queue = :user_buried_queue), 0) AS user_buried,
  coalesce(sum(queue = :learn_queue), 0) AS learn_count,
  max(
    0,
    coalesce(
      min(
        CASE
          WHEN queue = :learn_queue THEN due
          ELSE NULL
        END
      ),
      0
    )
  ) AS first_learn_due
FROM cards
WHERE did IN (
    SELECT id
    FROM active_decks
  )