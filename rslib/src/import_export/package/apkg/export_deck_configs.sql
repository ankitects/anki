INSERT INTO other.deck_config
SELECT *
FROM deck_config
WHERE id IN :ids