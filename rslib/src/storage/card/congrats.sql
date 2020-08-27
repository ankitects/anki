select sum(
        queue in (:review_queue, :day_learn_queue)
        and due <= :today
    ) as review_count,
    sum(queue = :new_queue) as new_count,
    sum(queue = :sched_buried_queue) as sched_buried,
    sum(queue = :user_buried_queue) as user_buried,
    sum(queue = :learn_queue) as learn_count,
    coalesce(
        min(
            case
                when queue = :learn_queue then due
                else null
            end
        ),
        0
    ) as first_learn_due
from cards
where did in (
        select id
        from active_decks
    )