create table media (
 fname text not null primary key,
 csum text,           -- null indicates deleted file
 mtime int not null,  -- zero if deleted
 dirty int not null
) without rowid;

create index idx_media_dirty on media (dirty) where dirty=1;

create table meta (dirMod int, lastUsn int); insert into meta values (0, 0);
