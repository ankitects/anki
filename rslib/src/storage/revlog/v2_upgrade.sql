UPDATE revlog
SET ease = ease + 1
WHERE ease IN (2, 3)
  AND type IN (0, 2);