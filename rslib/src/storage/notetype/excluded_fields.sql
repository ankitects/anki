DROP TABLE IF EXISTS excluded_fields;
CREATE TEMPORARY TABLE excluded_fields (
  ntid integer NOT NULL,
  ord integer NOT NULL,
  PRIMARY KEY (ntid, ord)
);
DROP TABLE IF EXISTS sort_fields;
CREATE TEMPORARY TABLE sort_fields (
  ntid integer PRIMARY KEY NOT NULL,
  ord integer NOT NULL
);