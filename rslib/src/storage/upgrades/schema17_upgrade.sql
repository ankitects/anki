drop table tags;
create table tags (
    id integer primary key not null,
    name text not null collate unicase,
    usn integer not null,
    config blob not null
);