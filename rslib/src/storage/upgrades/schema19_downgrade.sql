ALTER TABLE revlog DROP COLUMN reveal_millis;
UPDATE col
SET ver = 18;