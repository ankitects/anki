INSERT
  OR IGNORE INTO probes (
    id,
    cid,
    question,
    answer,
    citation,
    provenance
  )
VALUES (
    (
      CASE
        WHEN ?1
        AND ?2 IN (
          SELECT id
          FROM probes
        ) THEN (
          SELECT max(id) + 1
          FROM probes
        )
        ELSE ?2
      END
    ),
    ?,
    ?,
    ?,
    ?,
    ?
  )