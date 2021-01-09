CREATE TABLE media (
  fname text NOT NULL PRIMARY KEY,
  -- null indicates deleted file
  csum text,
  -- zero if deleted
  mtime int NOT NULL,
  dirty int NOT NULL
) without rowid;
CREATE INDEX idx_media_dirty ON media (dirty)
WHERE dirty = 1;
CREATE TABLE meta (dirMod int, lastUsn int);
INSERT INTO meta
VALUES (0, 0);