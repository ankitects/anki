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
      AND queue IN (:review_queue, :daylearn_queue)
    )
  );