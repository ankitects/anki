CREATE TABLE media (
  fname text NOT NULL PRIMARY KEY,
  csum text,
  -- null indicates deleted file
  mtime int NOT NULL,
  -- zero if deleted
  dirty int NOT NULL
) without rowid;
CREATE INDEX idx_media_dirty ON media (dirty)
WHERE dirty = 1;
CREATE TABLE meta (dirMod int, lastUsn int);
INSERT INTO meta
VALUES (0, 0);