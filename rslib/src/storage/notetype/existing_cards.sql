select
  id,
  nid,
  ord,
  -- original deck
  (
    case
      odid
      when 0 then did
      else odid
    end
  ),
  -- new position if card is empty
  (
    case
      type
      when 0 then (
        case
          odue
          when 0 then max(0, due)
          else max(odue, 0)
        end
      )
      else null
    end
  )
from cards c