drop table if exists sort_order;
create temporary table sort_order (
  pos integer primary key,
  ntid integer not null,
  ord integer not null,
  unique(ntid, ord)
);
insert into sort_order (ntid, ord)
select
  ntid,
  ord
from templates
order by
  name