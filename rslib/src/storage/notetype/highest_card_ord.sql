select coalesce(max(ord), 0)
from cards
where nid in (
        select id
        from notes
        where mid = ?
    )