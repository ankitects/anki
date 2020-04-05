create table config (
  key text not null primary key,
  usn integer not null,
  mtime_secs integer not null,
  val text not null
) without rowid;
update col
set
  ver = 14;