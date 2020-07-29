select did,
  sum(queue = :new_queue),
  sum(
    queue = :review_queue
    and due <= :day_cutoff
  ),
  -- learning
  sum(
    (
      case
        :sched_ver
        when 2 then (
          -- v2 scheduler
          (
            queue = :learn_queue
            and due < :learn_cutoff
          )
          or (
            queue = :daylearn_queue
            and due <= :day_cutoff
          )
          or (
            queue = :preview_queue
            and due <= :learn_cutoff
          )
        )
        else (
          -- v1 scheduler
          case
            when queue = :learn_queue
            and due < :learn_cutoff then left / 1000
            when queue = :daylearn_queue
            and due <= :day_cutoff then 1
            else 0
          end
        )
      end
    )
  )
from cards
where queue >= 0