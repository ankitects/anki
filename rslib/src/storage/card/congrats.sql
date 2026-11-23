-- Each value is an index seek in (did, queue, due) per active deck, so the
-- query does not visit every card of the deck.
SELECT EXISTS (
    SELECT 1
    FROM cards
    WHERE did IN (
        SELECT id
        FROM active_decks
      )
      AND queue IN (:review_queue, :day_learn_queue)
      AND due <= :today
  ) AS review_remaining,
  EXISTS (
    SELECT 1
    FROM cards
    WHERE did IN (
        SELECT id
        FROM active_decks
      )
      AND queue = :new_queue
  ) AS new_remaining,
  EXISTS (
    SELECT 1
    FROM cards
    WHERE did IN (
        SELECT id
        FROM active_decks
      )
      AND queue = :sched_buried_queue
  ) AS sched_buried,
  EXISTS (
    SELECT 1
    FROM cards
    WHERE did IN (
        SELECT id
        FROM active_decks
      )
      AND queue = :user_buried_queue
  ) AS user_buried,
  (
    SELECT COUNT()
    FROM cards
    WHERE did IN (
        SELECT id
        FROM active_decks
      )
      AND queue = :learn_queue
  ) AS learn_count,
  max(
    0,
    coalesce(
      (
        SELECT min(due)
        FROM cards
        WHERE did IN (
            SELECT id
            FROM active_decks
          )
          AND queue = :learn_queue
      ),
      0
    )
  ) AS first_learn_due