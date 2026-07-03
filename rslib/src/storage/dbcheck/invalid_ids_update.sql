UPDATE "{target_table}"
SET "{id_column}" = (
    SELECT invalid_ids.new_id
    FROM invalid_ids
    WHERE invalid_ids.id = "{target_table}"."{id_column}"
    LIMIT 1
  )
WHERE "{target_table}"."{id_column}" IN (
    SELECT invalid_ids.id
    FROM invalid_ids
  );