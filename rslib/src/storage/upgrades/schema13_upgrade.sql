create table tags (
  tag text not null primary key collate unicase,
  usn integer not null
) without rowid;
update col
set
  ver = 13;