insert into deck_config (id, name, mtime_secs, usn, config)
values
  (
    (
      case
        when ?1 in (
          select
            id
          from deck_config
        ) then (
          select
            max(id) + 1
          from deck_config
        )
        else ?1
      end
    ),
    ?,
    ?,
    ?,
    ?
  );