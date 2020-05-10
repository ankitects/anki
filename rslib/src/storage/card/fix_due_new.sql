update cards
set
  due = (
    case
      when type = 0
      and queue != 4 then 1000000 + due % 1000000
      else due
    end
  ),
  mod = ?1,
  usn = ?2
where
  due != (
    case
      when type = 0
      and queue != 4 then 1000000 + due % 1000000
      else due
    end
  )
  and due >= 1000000;