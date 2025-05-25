WITH searched_revlogs AS (
  SELECT *
  FROM revlog
  WHERE ease > 0
    AND cid IN search_cids
  ORDER BY id DESC -- Use the last 10_000 reviews
  LIMIT 10000
), average_pass AS (
  SELECT AVG(time)
  FROM searched_revlogs
  WHERE ease > 1
),
lapse_count AS (
  SELECT COUNT(time) AS lapse_count
  FROM searched_revlogs
  WHERE ease = 1
    AND type = 1
),
fail_sum AS (
  SELECT SUM(time) AS total_fail_time
  FROM searched_revlogs
  WHERE (
      ease = 1
      AND type = 1
    )
    OR type = 2
),
-- (sum(Relearning) + sum(Lapses)) / count(Lapses)
average_fail AS (
  SELECT total_fail_time * 1.0 / NULLIF(lapse_count, 0) AS avg_fail_time
  FROM fail_sum,
    lapse_count
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