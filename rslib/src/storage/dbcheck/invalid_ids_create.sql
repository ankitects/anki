DROP TABLE IF EXISTS invalid_ids;
CREATE TEMPORARY TABLE invalid_ids AS WITH max_existing_valid_id AS (
  SELECT coalesce(max(id), 0) AS max_id
  FROM "{source_table}"
  WHERE id <= "{max_valid_id}"
),
first_new_id AS (
  SELECT CASE
      WHEN "{new_id}" > (
        SELECT max_id
        FROM max_existing_valid_id
      ) THEN "{new_id}"
      ELSE (
        SELECT max_id
        FROM max_existing_valid_id
      ) + 1
    END AS id
)
SELECT id,
  (
    SELECT id
    FROM first_new_id
  ) + row_number() OVER (
    ORDER BY id
  ) - 1 AS new_id
FROM "{source_table}"
WHERE id > "{max_valid_id}";
CREATE INDEX invalid_ids_id_idx ON invalid_ids (id);