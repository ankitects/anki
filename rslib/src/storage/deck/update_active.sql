insert into active_decks
select id
from decks
where name = ?
    or (
        name >= ?
        and name < ?
    )