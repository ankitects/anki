select
  case
    when ?1 in (
      select
        id
      from decks
    ) then (
      select
        max(id) + 1
      from decks
    )
    else ?1
  end;