INSERT INTO search_cids
SELECT id
FROM cards
WHERE id != :card_id
  AND nid = :note_id
  AND (
    (
      :include_new
      AND queue = :new_queue
    )
    OR (
      :include_reviews
      AND queue = :review_queue
    )
    OR (
      :include_day_learn
      AND queue = :daylearn_queue
    )
  );