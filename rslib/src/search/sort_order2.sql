drop table if exists sort_order;
create temporary table sort_order (k1 int, k2 int, v text, primary key (k1, k2)) without rowid;