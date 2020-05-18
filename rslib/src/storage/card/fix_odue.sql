update cards
set
  odue = (
    case
      when odue > 0
      and (
        type = 1
        or queue = 2
      )
      and not odid then 0
      else min(max(round(odue), -2147483648), 2147483647)
    end
  ),
  mod = ?1,
  usn = ?2
where
  odue != (
    case
      when odue > 0
      and (
        type = 1
        or queue = 2
      )
      and not odid then 0
      else min(max(round(odue), -2147483648), 2147483647)
    end
  );