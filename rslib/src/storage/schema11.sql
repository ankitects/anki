create table col
(
    id     integer primary key,
    crt    integer not null,
    mod    integer not null,
    scm    integer not null,
    ver    integer not null,
    dty    integer not null,
    usn    integer not null,
    ls     integer not null,
    conf   text    not null,
    models text    not null,
    decks  text    not null,
    dconf  text    not null,
    tags   text    not null
);

create table notes
(
    id    integer primary key,
    guid  text    not null,
    mid   integer not null,
    mod   integer not null,
    usn   integer not null,
    tags  text    not null,
    flds  text    not null,
    sfld  integer not null,
    csum  integer not null,
    flags integer not null,
    data  text    not null
);

create table cards
(
    id     integer primary key,
    nid    integer not null,
    did    integer not null,
    ord    integer not null,
    mod    integer not null,
    usn    integer not null,
    type   integer not null,
    queue  integer not null,
    due    integer not null,
    ivl    integer not null,
    factor integer not null,
    reps   integer not null,
    lapses integer not null,
    left   integer not null,
    odue   integer not null,
    odid   integer not null,
    flags  integer not null,
    data   text    not null
);

create table revlog
(
    id      integer primary key,
    cid     integer not null,
    usn     integer not null,
    ease    integer not null,
    ivl     integer not null,
    lastIvl integer not null,
    factor  integer not null,
    time    integer not null,
    type    integer not null
);

create table graves
(
    usn  integer not null,
    oid  integer not null,
    type integer not null
);

-- syncing
create index ix_notes_usn on notes (usn);
create index ix_cards_usn on cards (usn);
create index ix_revlog_usn on revlog (usn);
-- card spacing, etc
create index ix_cards_nid on cards (nid);
-- scheduling and deck limiting
create index ix_cards_sched on cards (did, queue, due);
-- revlog by card
create index ix_revlog_cid on revlog (cid);
-- field uniqueness
create index ix_notes_csum on notes (csum);

insert into col values (1,0,0,0,0,0,0,0,'{}','{}','{}','{}','{}');
