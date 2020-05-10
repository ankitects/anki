update cards
set
  due = (
    case
      when queue = 2
      and due > 100000 then ?1
      else min(max(round(due), -2147483648), 2147483647)
    end
  ),
  mod = ?2,
  usn = ?3
where
  due != (
    case
      when queue = 2
      and due > 100000 then ?1
      else min(max(round(due), -2147483648), 2147483647)
    end
  );