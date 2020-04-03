create table deck_config (
  id integer primary key not null,
  name text not null collate unicase,
  mtime_secs integer not null,
  usn integer not null,
  config text not null
);
create table tags (
  tag text not null primary key collate unicase,
  usn integer not null
) without rowid;
update col
set
  ver = 12;