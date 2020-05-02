drop table if exists sort_order;
create temporary table sort_order (
  pos integer primary key,
  ntid integer not null unique
);
insert into sort_order (ntid)
select
  id
from notetypes
order by
  name;