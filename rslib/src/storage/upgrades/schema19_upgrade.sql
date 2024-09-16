CREATE TABLE excess_reviews (
  deck_id integer PRIMARY KEY,
  reviews integer NOT NULL
);

CREATE UNIQUE INDEX idx_excess_reviews_deck_id ON excess_reviews (deck_id);
UPDATE col
SET ver = 19;