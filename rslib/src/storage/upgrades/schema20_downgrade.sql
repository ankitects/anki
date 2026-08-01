ALTER TABLE revlog DROP COLUMN data;
DROP TABLE probes;
UPDATE col
SET ver = 19;