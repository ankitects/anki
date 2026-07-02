ALTER TABLE graves
  RENAME TO graves_old;
CREATE TABLE graves (
  usn integer NOT NULL,
  oid integer NOT NULL,
  type integer NOT NULL
);
INSERT INTO graves (usn, oid, type)
SELECT usn,
  oid,
  type
FROM graves_old;
DROP TABLE graves_old;
UPDATE col
SET ver = 17;