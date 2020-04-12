drop table config;
drop table deck_config;
drop table tags;
drop table fields;
drop table templates;
drop table notetypes;
update col
set
  ver = 11;