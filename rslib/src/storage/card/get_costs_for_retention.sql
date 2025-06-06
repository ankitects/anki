WITH searched_revlogs AS (
  SELECT *,
    RANK() OVER (
      PARTITION BY cid
      ORDER BY id ASC
    ) AS rank_num
  FROM revlog
  WHERE ease > 0
    AND cid IN search_cids
  ORDER BY id DESC -- Use the last 10_000 reviews
  LIMIT 10000
), average_pass AS (
  SELECT AVG(time)
  FROM searched_revlogs
  WHERE ease > 1
    AND type = 1
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
),
initial_pass_rate AS (
  SELECT AVG(
      CASE
        WHEN ease > 1 THEN 1.0
        ELSE 0.0
      END
    ) AS initial_pass_rate
  FROM searched_revlogs
  WHERE rank_num = 1
),
pass_cnt AS (
  SELECT COUNT(*) AS cnt
  FROM searched_revlogs
  WHERE ease > 1
    AND type = 1
),
fail_cnt AS (
  SELECT COUNT(*) AS cnt
  FROM searched_revlogs
  WHERE ease = 1
    AND type = 1
),
learn_cnt AS (
  SELECT COUNT(*) AS cnt
  FROM searched_revlogs
  WHERE type = 0
)
SELECT *
FROM average_pass,
  average_fail,
  average_learn,
  initial_pass_rate,
  pass_cnt,
  fail_cnt,
  learn_cnt;