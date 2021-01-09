DROP TABLE tags;
CREATE TABLE tags (
  id integer PRIMARY KEY NOT NULL,
  name text NOT NULL COLLATE unicase,
  usn integer NOT NULL,
  config blob NOT NULL
);