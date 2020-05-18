drop table if exists sort_order;
create temporary table sort_order (
  pos integer primary key,
  did integer not null unique
);
insert into sort_order (did)
select
  id
from decks
order by
  name;