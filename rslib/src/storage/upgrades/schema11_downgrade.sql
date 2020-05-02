drop table config;
drop table deck_config;
drop table tags;
drop table fields;
drop table templates;
drop table notetypes;
drop table decks;
drop index idx_cards_odid;
update col
set
  ver = 11;