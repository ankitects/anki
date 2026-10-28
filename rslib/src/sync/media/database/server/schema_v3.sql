BEGIN exclusive;
CREATE TABLE IF NOT EXISTS media (
  fname text NOT NULL PRIMARY KEY,
  csum blob,
  sz int NOT NULL,
  usn int NOT NULL,
  deleted int NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_usn ON media (usn);
CREATE TABLE IF NOT EXISTS meta (usn int NOT NULL, sz int NOT NULL);
INSERT INTO meta (usn, sz)
VALUES (0, 0);
pragma user_version = 3;
COMMIT;