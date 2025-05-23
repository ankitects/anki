WITH searched_revlogs AS (
  SELECT *
  FROM revlog
  WHERE cid IN search_cids
  ORDER BY id DESC -- Use the last 10000 cards
  LIMIT 10000
), average_pass AS (
  SELECT AVG(time)
  FROM searched_revlogs
  WHERE ease > 1
),
average_fail AS (
  SELECT AVG(time)
  FROM searched_revlogs
  WHERE ease = 1
),
-- Can lead to cards with partial learn histories skewing the time 
summed_learns AS (
  SELECT cid,
    SUM(time) AS total_time
  FROM searched_revlogs
  WHERE searched_revlogs.type = 0
  GROUP BY cid
),
average_learn AS (
  SELECT AVG(total_time) AS avg_learn_time
  FROM summed_learns
)
SELECT *
FROM average_pass,
  average_fail,
  average_learn;