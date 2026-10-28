ALTER TABLE graves
  RENAME TO graves_old;
CREATE TABLE graves (
  oid integer NOT NULL,
  type integer NOT NULL,
  usn integer NOT NULL,
  PRIMARY KEY (oid, type)
) WITHOUT ROWID;
INSERT
  OR IGNORE INTO graves (oid, type, usn)
SELECT oid,
  type,
  usn
FROM graves_old;
DROP TABLE graves_old;
CREATE INDEX idx_graves_pending ON graves (usn);
UPDATE col
SET ver = 18;