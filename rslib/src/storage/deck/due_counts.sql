select
  did,
  -- new
  sum(queue = ?1),
  -- reviews
  sum(
    queue = ?2
    and due <= ?3
  ),
  -- learning
  sum(
    (
      case
        -- v2 scheduler
        ?4
        when 2 then (
          queue = ?5
          and due < ?6
        )
        or (
          queue = ?7
          and due <= ?3
        )
        else (
          -- v1 scheduler
          case
            when queue = ?5
            and due < ?6 then left / 1000
            when queue = ?7
            and due <= ?3 then 1
            else 0
          end
        )
      end
    )
  )
from cards
where
  queue >= 0