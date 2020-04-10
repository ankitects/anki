create table deck_config (
  id integer primary key not null,
  name text not null collate unicase,
  mtime_secs integer not null,
  usn integer not null,
  config blob not null
);
create table config (
  key text not null primary key,
  usn integer not null,
  mtime_secs integer not null,
  val blob not null
) without rowid;
create table tags (
  tag text not null primary key collate unicase,
  usn integer not null
) without rowid;
update col
set
  ver = 14;