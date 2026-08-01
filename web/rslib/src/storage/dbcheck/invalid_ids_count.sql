SELECT (
    SELECT COUNT(*)
    FROM notes
    WHERE id > :cutoff
  ) + (
    SELECT COUNT(*)
    FROM cards
    WHERE id > :cutoff
  ) + (
    SELECT COUNT(*)
    FROM revlog
    WHERE id > :cutoff
  );