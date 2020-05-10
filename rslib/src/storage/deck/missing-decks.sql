select
  distinct did
from cards
where
  did not in (
    select
      id
    from decks
  );