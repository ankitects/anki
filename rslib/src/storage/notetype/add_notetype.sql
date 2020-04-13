insert into notetypes (id, name, mtime_secs, usn, config)
values
  (
    (
      case
        when ?1 in (
          select
            id
          from notetypes
        ) then (
          select
            max(id) + 1
          from notetypes
        )
        else ?1
      end
    ),
    ?,
    ?,
    ?,
    ?
  );