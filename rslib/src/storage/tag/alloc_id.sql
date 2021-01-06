select
  case
    when ?1 in (
      select
        id
      from tags
    ) then (
      select
        max(id) + 1
      from tags
    )
    else ?1
  end;