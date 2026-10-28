DROP TABLE IF EXISTS sort_order;
CREATE TEMPORARY TABLE sort_order (
  pos integer PRIMARY KEY,
  nid integer NOT NULL UNIQUE
);
INSERT INTO sort_order (nid)
SELECT nid
FROM cards
  JOIN (
    SELECT id,
      row_number() OVER(
        ORDER BY name
      ) AS pos
    FROM decks
  ) decks ON cards.did = decks.id
GROUP BY nid
ORDER BY COUNT(DISTINCT did),
  decks.pos;