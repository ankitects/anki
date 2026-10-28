DROP TABLE config;
DROP TABLE deck_config;
DROP TABLE tags;
DROP TABLE fields;
DROP TABLE templates;
DROP TABLE notetypes;
DROP TABLE decks;
DROP INDEX idx_cards_odid;
DROP INDEX idx_notes_mid;
UPDATE col
SET ver = 11;