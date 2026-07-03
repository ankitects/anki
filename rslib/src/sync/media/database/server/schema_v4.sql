-- csum is no longer nulled on deletion
-- sz renamed to size
-- deleted renamed to mtime
BEGIN exclusive;
ALTER TABLE media
  RENAME TO media_tmp;
DROP INDEX ix_usn;
CREATE TABLE media (
  fname text NOT NULL PRIMARY KEY,
  csum blob NOT NULL,
  -- if zero, file has been deleted
  size int NOT NULL,
  usn int NOT NULL,
  mtime int NOT NULL
);
INSERT INTO media (fname, csum, size, usn, mtime)
SELECT fname,
  csum,
  sz,
  usn,
  deleted
FROM media_tmp
WHERE csum IS NOT NULL;
DROP TABLE media_tmp;
CREATE INDEX ix_usn ON media (usn);
DROP TABLE meta;
-- columns renamed; file count added
CREATE TABLE meta (
  last_usn int NOT NULL,
  total_bytes int NOT NULL,
  total_nonempty_files int NOT NULL
);
INSERT INTO meta (last_usn, total_bytes, total_nonempty_files)
SELECT coalesce(max(usn), 0),
  coalesce(sum(size), 0),
  0
FROM media;
UPDATE meta
SET total_nonempty_files = (
    SELECT COUNT(*)
    FROM media
    WHERE size > 0
  );
pragma user_version = 4;
COMMIT;
vacuum;