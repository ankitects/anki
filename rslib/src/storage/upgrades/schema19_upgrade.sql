ALTER TABLE revlog
ADD COLUMN reveal_millis integer;
UPDATE col
SET ver = 19;