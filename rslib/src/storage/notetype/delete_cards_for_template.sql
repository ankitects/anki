delete from cards
where
  nid in (
    select
      id
    from notes
    where
      mid = ?
  )
  and ord = ?;