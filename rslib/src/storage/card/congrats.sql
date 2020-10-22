select coalesce(
        sum(
            queue in (:review_queue, :day_learn_queue)
            and due <= :today
        ),
        0
    ) as review_count,
    coalesce(sum(queue = :new_queue), 0) as new_count,
    coalesce(sum(queue = :sched_buried_queue), 0) as sched_buried,
    coalesce(sum(queue = :user_buried_queue), 0) as user_buried,
    coalesce(sum(queue = :learn_queue), 0) as learn_count,
    max(
        0,
        coalesce(
            min(
                case
                    when queue = :learn_queue then due
                    else null
                end
            ),
            0
        )
    ) as first_learn_due
from cards
where did in (
        select id
        from active_decks
    )