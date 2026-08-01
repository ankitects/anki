ALTER TABLE revlog
ADD COLUMN data text NOT NULL DEFAULT '';
CREATE TABLE probes (
  id integer PRIMARY KEY,
  cid integer NOT NULL,
  question text NOT NULL,
  answer text NOT NULL,
  citation text NOT NULL,
  provenance text NOT NULL DEFAULT ''
);
CREATE INDEX idx_probes_cid ON probes (cid);
UPDATE col
SET ver = 20;
