insert into search_cids
select id
from cards
where due >= ?
    and type = ?