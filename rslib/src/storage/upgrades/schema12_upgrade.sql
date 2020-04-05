create table deck_config (
  id integer primary key not null,
  name text not null collate unicase,
  mtime_secs integer not null,
  usn integer not null,
  config blob not null
);
update col
set
  ver = 12;